package io.toaruesper.addon.senior;

import io.github.pinchan4273.reacademycraft.api.AbilityId;
import io.github.pinchan4273.reacademycraft.api.ActivationKind;
import io.github.pinchan4273.reacademycraft.api.CategoryDefinition;
import io.github.pinchan4273.reacademycraft.api.Presentation;
import io.github.pinchan4273.reacademycraft.api.SkillDefinition;
import io.github.pinchan4273.reacademycraft.api.TreePosition;
import io.github.pinchan4273.reacademycraft.api.forge.RegisterAbilitiesEvent;
import io.toaruesper.addon.ToaruEsper;
import java.util.List;

/** Registers the Senior category and its skills with Re:AcademyCraft. */
public final class SeniorAbilities {
    public static final AbilityId SENIOR = id("senior");
    public static final AbilityId SENIOR_FACTOR = id("senior_factor");
    public static final AbilityId BLACK_HOLE = id("black_hole");

    private SeniorAbilities() { }

    private static AbilityId id(String path) { return AbilityId.parse(ToaruEsper.MOD_ID + ":" + path); }

    private static Presentation shown(AbilityId skill) {
        String base = ToaruEsper.MOD_ID + ".skill." + skill.path();
        return new Presentation(base, base + ".desc", null);
    }

    public static void register(RegisterAbilitiesEvent event) {
        var abilities = event.registrar(ToaruEsper.MOD_ID);
        // Five numbers = how many usable skills the category has at levels 1..5 (Re:AcademyCraft uses this for level-up cost).
        abilities.category(new CategoryDefinition(SENIOR, SENIOR_FACTOR, List.of(0, 0, 0, 1, 0),
                new Presentation(ToaruEsper.MOD_ID + ".ability.senior", null, null)));

        // Black Hole: level 4, no prerequisite, shows on the developer machine's skill tree.
        // TOGGLE: it keeps running for a few seconds; pressing the key again collapses it early.
        abilities.skill(new SkillDefinition(BLACK_HOLE, SENIOR, 4, List.of(), ActivationKind.TOGGLE,
                shown(BLACK_HOLE), new TreePosition(100, 50, null)));

        event.behaviors(ToaruEsper.MOD_ID).behavior(BLACK_HOLE, new BlackHoleBehavior());
    }
}
