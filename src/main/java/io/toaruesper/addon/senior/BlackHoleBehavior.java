package io.toaruesper.addon.senior;

import io.github.pinchan4273.reacademycraft.api.ActivationOutcome;
import io.github.pinchan4273.reacademycraft.api.StopReason;
import io.github.pinchan4273.reacademycraft.api.mc.AbilityActor;
import io.github.pinchan4273.reacademycraft.api.mc.AcademySafety;
import io.github.pinchan4273.reacademycraft.api.mc.ActivationContext;
import io.github.pinchan4273.reacademycraft.api.mc.SkillBehavior;
import io.toaruesper.addon.net.SeniorNetwork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStoppedEvent;

/**
 * Black Hole (Senior), version 2. All the gameplay runs on the server; clients only get small messages to draw it.
 * <ol>
 * <li>Formation (1 s): the hole opens where the caster looks. The pull area starts small and grows over 2 seconds.</li>
 * <li>Open: creatures, loose items and projectiles that have a clear line to the hole are dragged toward the centre.
 *     Creatures near the centre take damage once a second, and that damage grows as the hole gets older.
 *     Projectiles (not the caster's) are bent toward it and swallowed near the centre.</li>
 * <li>Critical (last 0.6 s): the hole contracts and the pull is multiplied.</li>
 * <li>Collapse: heavy damage (less at the edge); survivors and loose items are thrown outward. No blocks are ever changed.</li>
 * </ol>
 * Every attack goes through Re:AcademyCraft's safety check, so protected areas, PvP and team rules still apply.
 * Pressing the skill key again collapses the hole early.
 */
public final class BlackHoleBehavior implements SkillBehavior {
    /** One running hole per caster. Only touched from the server thread. */
    private static final Map<UUID, Hole> ACTIVE = new HashMap<>();
    private static int nextNetId = 1;

    private static final int GROW_TICKS = 20;
    private static final int REACH_TICKS = 40;
    private static final int CRITICAL_TICKS = 12;
    private static final int RESYNC_TICKS = 40;

    private static final class Hole {
        final int netId;
        final Vec3 center;
        final int duration;
        /** Skill-experience scaling of the damage (about 0.75x to 1.25x). */
        final float scale;
        /** Whether the safety check allows hurting each creature (asked rarely, not every tick). */
        final Map<UUID, Boolean> allowed = new HashMap<>();
        /** Whether each thing has a clear line to the centre (refreshed every half second). */
        final Map<UUID, Boolean> sight = new HashMap<>();

        Hole(int netId, Vec3 center, int duration, float scale) {
            this.netId = netId;
            this.center = center;
            this.duration = duration;
            this.scale = scale;
        }
    }

    private record Found(List<LivingEntity> living, List<Entity> objects) { }

    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    @Override
    public ActivationOutcome start(ActivationContext c) {
        var actor = c.actor();
        var body = actor.entity();
        if (body == null || c.naturalUser() == null) return ActivationOutcome.REFUSED_INVALID;

        // Re:AcademyCraft already blocks a second Black Hole while one runs, so an old entry here is left over: clear it.
        var stale = ACTIVE.remove(actor.id());
        if (stale != null) SeniorNetwork.sendEnd(actor.level(), stale.center, stale.netId, false);

        double range = SeniorConfig.BH_RANGE.get();
        Vec3 eye = actor.position();
        Vec3 end = eye.add(actor.look().scale(range));
        var hit = actor.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, body));
        Vec3 center;
        if (hit.getType() == HitResult.Type.MISS) {
            center = end;
        } else {
            // Float a little off the surface so the hole is not buried in the wall or floor.
            var face = hit.getDirection();
            center = hit.getLocation().add(face.getStepX() * 1.5, face.getStepY() * 1.5, face.getStepZ() * 1.5);
        }

        if (!c.pay(SeniorConfig.BH_CP_COST.get().floatValue(), SeniorConfig.BH_OVERLOAD_COST.get().floatValue())) {
            return ActivationOutcome.REFUSED_RESOURCE;
        }

        int duration = Math.max(GROW_TICKS + CRITICAL_TICKS, Math.round(SeniorConfig.BH_DURATION_SECONDS.get().floatValue() * 20f));
        var hole = new Hole(nextNetId++, center, duration, 0.75f + 0.5f * c.proficiency());
        ACTIVE.put(actor.id(), hole);

        // The cooldown counts from now, so it is the open time plus the configured cooldown.
        int cooldown = duration + Math.round(SeniorConfig.BH_COOLDOWN_SECONDS.get().floatValue() * 20f);
        c.setCooldown(Math.min(72000, cooldown));
        c.addProficiency(0.01f);

        var level = actor.level();
        SeniorNetwork.sendStart(level, hole.netId, center, SeniorConfig.BH_RADIUS.get().floatValue(), duration, 0);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 0.5f);
        return ActivationOutcome.STARTED;
    }

    @Override
    public boolean tick(ActivationContext c) {
        var actor = c.actor();
        var hole = ACTIVE.get(actor.id());
        if (hole == null) return false;
        if (!actor.valid()) {
            finish(actor, hole, false);
            return false;
        }
        int age = c.age();
        if (age >= hole.duration) {
            finish(actor, hole, true);
            return false;
        }
        var level = actor.level();
        double radius = SeniorConfig.BH_RADIUS.get();
        if (age % 40 == 0) hole.allowed.clear();
        if (age % 10 == 0) hole.sight.clear();
        if (age > 0 && age % RESYNC_TICKS == 0) {
            SeniorNetwork.sendStart(level, hole.netId, hole.center, (float) radius, hole.duration, age);
        }
        float progress = Mth.clamp((age - GROW_TICKS) / (float) Math.max(1, hole.duration - GROW_TICKS), 0f, 1f);
        if (age % 30 == 0) {
            level.playSound(null, hole.center.x, hole.center.y, hole.center.z, SoundEvents.BEACON_AMBIENT,
                    SoundSource.PLAYERS, 1.2f, 0.5f + 0.5f * progress);
        }

        double startReach = SeniorConfig.BH_START_REACH.get();
        double reach = radius * (startReach + (1.0 - startReach) * smooth(Math.min(1f, age / (float) REACH_TICKS)));
        boolean critical = age >= hole.duration - CRITICAL_TICKS;
        double boost = critical ? SeniorConfig.BH_CRITICAL_BOOST.get() : 1.0;
        double pull = SeniorConfig.BH_PULL.get();
        double damageRadius = SeniorConfig.BH_DAMAGE_RADIUS.get();
        float perSecond = SeniorConfig.BH_DAMAGE_PER_SECOND.get().floatValue();
        float ramp = Math.min(1f, age / (float) GROW_TICKS);
        float damageRamp = 1f + (SeniorConfig.BH_DAMAGE_RAMP.get().floatValue() - 1f) * progress;
        boolean damageNow = age >= GROW_TICKS && age % 20 == 0;

        var found = gather(actor, hole, reach);

        for (var e : found.living()) {
            if (!allowed(actor, hole, e) || !sees(actor, hole, e)) continue;
            pullLiving(hole, e, reach, pull, ramp * boost, critical ? 1.5 : 1.0);
            if (damageNow) {
                double dist = e.getBoundingBox().getCenter().distanceTo(hole.center);
                if (dist <= damageRadius) {
                    // Normal damage at the edge of the damage zone, double right at the core.
                    float amount = (float) (perSecond * hole.scale * damageRamp * (1.0 + (1.0 - dist / damageRadius)));
                    AcademySafety.attack(actor, e, SeniorAbilities.BLACK_HOLE, amount);
                }
            }
        }

        for (var o : found.objects()) {
            boolean projectile = o instanceof Projectile;
            if (!projectile && !sees(actor, hole, o)) continue;
            double dist = o.getBoundingBox().getCenter().distanceTo(hole.center);
            if (projectile && dist <= damageRadius) {
                o.discard(); // swallowed
                continue;
            }
            pullObject(hole, o, reach, pull, ramp * boost);
        }
        return true;
    }

    @Override
    public void stop(ActivationContext c, StopReason reason) {
        var actor = c.actor();
        var hole = ACTIVE.get(actor.id());
        if (hole == null) return; // already finished in tick()
        // Pressing the key again (after the hole has formed) triggers the collapse early. Any other reason just closes it quietly.
        finish(actor, hole, reason == StopReason.SWITCHED_OFF && c.age() >= GROW_TICKS);
    }

    private static void finish(AbilityActor actor, Hole hole, boolean collapse) {
        ACTIVE.remove(actor.id());
        if (collapse) collapse(actor, hole, SeniorConfig.BH_RADIUS.get());
        SeniorNetwork.sendEnd(actor.level(), hole.center, hole.netId, collapse);
    }

    private static float smooth(float x) {
        float c = Mth.clamp(x, 0f, 1f);
        return c * c * (3f - 2f * c);
    }

    /** Everything inside the reach that may be affected: creatures, and loose items / projectiles (if enabled). Closest first, capped. */
    private static Found gather(AbilityActor actor, Hole hole, double reach) {
        var self = actor.entity();
        boolean bosses = SeniorConfig.BH_AFFECT_BOSSES.get();
        boolean absorb = SeniorConfig.BH_ABSORB_PROJECTILES.get();
        boolean items = SeniorConfig.BH_PULL_ITEMS.get();
        double reachSq = reach * reach;
        var box = new AABB(hole.center, hole.center).inflate(reach);
        List<Entity> all = actor.level().getEntitiesOfClass(Entity.class, box, e -> {
            if (e == self || !e.isAlive() || e.isSpectator()) return false;
            if (e.getBoundingBox().getCenter().distanceToSqr(hole.center) > reachSq) return false;
            if (e instanceof LivingEntity) {
                if (e instanceof Player player && player.isCreative()) return false;
                return bosses || !(e instanceof EnderDragon || e instanceof WitherBoss);
            }
            if (e instanceof Projectile proj) {
                return absorb && proj.getOwner() != self && proj.getDeltaMovement().lengthSqr() > 0.0025;
            }
            return items && e instanceof ItemEntity;
        });
        var living = new ArrayList<LivingEntity>();
        var objects = new ArrayList<Entity>();
        for (var e : all) {
            if (e instanceof LivingEntity le) living.add(le);
            else objects.add(e);
        }
        return new Found(closest(living, hole, SeniorConfig.BH_MAX_TARGETS.get()),
                closest(objects, hole, SeniorConfig.BH_MAX_OBJECTS.get()));
    }

    private static <T extends Entity> List<T> closest(List<T> list, Hole hole, int max) {
        if (list.size() <= max) return list;
        list.sort(Comparator.comparingDouble((T e) -> e.getBoundingBox().getCenter().distanceToSqr(hole.center)));
        return new ArrayList<>(list.subList(0, max));
    }

    private static boolean allowed(AbilityActor actor, Hole hole, LivingEntity e) {
        return hole.allowed.computeIfAbsent(e.getUUID(), k -> AcademySafety.mayAttack(actor, e, SeniorAbilities.BLACK_HOLE));
    }

    /** Whether there is a clear line between the hole and the thing (walls protect). Checked from its middle and its eyes. */
    private static boolean sees(AbilityActor actor, Hole hole, Entity e) {
        if (!SeniorConfig.BH_LINE_OF_SIGHT.get()) return true;
        return hole.sight.computeIfAbsent(e.getUUID(), k ->
                clear(actor, hole, e.getBoundingBox().getCenter()) || clear(actor, hole, e.getEyePosition()));
    }

    private static boolean clear(AbilityActor actor, Hole hole, Vec3 to) {
        return actor.level().clip(new ClipContext(hole.center, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor.entity()))
                .getType() == HitResult.Type.MISS;
    }

    private static void pullLiving(Hole hole, LivingEntity e, double reach, double pull, double strength, double speedCap) {
        Vec3 toCentre = hole.center.subtract(e.getBoundingBox().getCenter());
        double dist = toCentre.length();
        Vec3 v = e.getDeltaMovement();
        if (dist < 0.8) {
            // At the core: hold still instead of swinging back and forth.
            v = v.scale(0.4);
        } else {
            double falloff = 0.35 + 0.65 * (1.0 - Math.min(1.0, dist / reach));
            v = v.scale(0.55).add(toCentre.scale(pull * strength * falloff / dist));
            if (e.onGround()) v = v.add(0, 0.12, 0); // lift off the ground so friction does not hold them
            if (v.lengthSqr() > speedCap * speedCap) v = v.normalize().scale(speedCap);
        }
        e.setDeltaMovement(v);
        e.hurtMarked = true; // makes the server send the new velocity to players
        e.fallDistance = 0;
    }

    /** Loose items and projectiles: pulled harder than creatures, and they curve in. */
    private static void pullObject(Hole hole, Entity e, double reach, double pull, double strength) {
        Vec3 toCentre = hole.center.subtract(e.getBoundingBox().getCenter());
        double dist = toCentre.length();
        if (dist < 0.3) return;
        double falloff = 0.5 + 0.5 * (1.0 - Math.min(1.0, dist / reach));
        Vec3 v = e.getDeltaMovement().scale(0.7).add(toCentre.scale(pull * 1.4 * strength * falloff / dist));
        if (v.lengthSqr() > 2.56) v = v.normalize().scale(1.6);
        e.setDeltaMovement(v);
        e.hasImpulse = true;
        e.hurtMarked = true;
    }

    private static void collapse(AbilityActor actor, Hole hole, double radius) {
        var level = actor.level();
        float finalDamage = SeniorConfig.BH_FINAL_DAMAGE.get().floatValue() * hole.scale;
        double knockback = SeniorConfig.BH_KNOCKBACK.get();
        var found = gather(actor, hole, radius);
        for (var e : found.living()) {
            if (!allowed(actor, hole, e) || !sees(actor, hole, e)) continue;
            Vec3 off = e.getBoundingBox().getCenter().subtract(hole.center);
            double dist = off.length();
            double edge = Math.min(1.0, dist / radius);
            e.invulnerableTime = 0; // the final blow should land even right after a damage tick
            if (!AcademySafety.attack(actor, e, SeniorAbilities.BLACK_HOLE, (float) (finalDamage * (1.0 - 0.5 * edge)))) continue;
            if (!e.isAlive()) continue;
            Vec3 dir = dist > 1.0e-3 ? off.scale(1.0 / dist) : new Vec3(0, 1, 0);
            double k = knockback * (1.0 - 0.5 * edge);
            e.setDeltaMovement(dir.x * k, dir.y * k * 0.6 + 0.45, dir.z * k);
            e.hurtMarked = true;
            e.fallDistance = 0;
        }
        // Loose items that were collected near the centre are thrown out again.
        for (var o : found.objects()) {
            if (!(o instanceof ItemEntity) || !sees(actor, hole, o)) continue;
            Vec3 off = o.getBoundingBox().getCenter().subtract(hole.center);
            double dist = off.length();
            Vec3 dir = dist > 1.0e-3 ? off.scale(1.0 / dist) : new Vec3(0, 1, 0);
            double k = knockback * 0.6;
            o.setDeltaMovement(dir.x * k, dir.y * k * 0.5 + 0.35, dir.z * k);
            o.hasImpulse = true;
            o.hurtMarked = true;
        }
        level.playSound(null, hole.center.x, hole.center.y, hole.center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0f, 0.6f);
    }
}
