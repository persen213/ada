#!/usr/bin/env python3
"""Fails (exit code 1) if any add-on source uses Minecraft particles.

This is a text check on our own code. It cannot see what other mods do. It also blocks Re:AcademyCraft's
ExternalPresentation effects kit, because some of its effect styles spawn particles internally.
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
FORBIDDEN = [
    "addParticle", "addAlwaysVisibleParticle", "sendParticles", "ParticleTypes", "ParticleEngine",
    "ParticleOptions", "ParticleType", "SimpleParticleType", "ParticleProvider", "ParticleRenderType",
    "net.minecraft.client.particle", "net.minecraft.core.particles", "RegisterParticleProvidersEvent",
    "ExternalPresentation",
]

problems = []
for path in (ROOT / "src").rglob("*"):
    if not path.is_file():
        continue
    if "particles" in path.parts:  # a resource folder named particles would register particle definitions
        problems.append(f"{path.relative_to(ROOT)}: particle resource folder")
        continue
    if path.suffix not in {".java", ".json", ".toml", ".mcmeta"}:
        continue
    text = path.read_text(encoding="utf-8", errors="replace")
    for number, line in enumerate(text.splitlines(), 1):
        for word in FORBIDDEN:
            if word in line:
                problems.append(f"{path.relative_to(ROOT)}:{number}: uses '{word}'")

if problems:
    print("PARTICLE CHECK FAILED")
    print("\n".join(problems))
    sys.exit(1)
print("Particle check passed: no particle APIs or particle resources in src/.")
