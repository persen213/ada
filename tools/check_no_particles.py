#!/usr/bin/env python3
"""
Zero-particle check for Black Hole add-on.
Scans source files for forbidden particle-related terms and resource folders.
Exits with code 1 if any violations found.
"""
import os
import sys
import re

FORBIDDEN_PATTERNS = [
    r'ParticleTypes',
    r'addParticle',
    r'addAlwaysVisibleParticle',
    r'sendParticles',
    r'ParticleEngine',
    r'ParticleOptions',
    r'particle\s+type',
    r'particle\s+registration',
    r'particle\s+provider',
    r'particles[/\\]',  # particles resource folder
]

FORBIDDEN_WORDS = [
    'ParticleTypes',
    'addParticle',
    'addAlwaysVisibleParticle',
    'sendParticles',
    'ParticleEngine',
    'ParticleOptions',
]

def check_file(filepath):
    """Check a single file for forbidden patterns."""
    violations = []
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
            lines = content.split('\n')

        for i, line in enumerate(lines, 1):
            # Skip comments
            stripped = line.strip()
            if stripped.startswith('//') or stripped.startswith('*') or stripped.startswith('/*'):
                continue

            for pattern in FORBIDDEN_PATTERNS:
                if re.search(pattern, line, re.IGNORECASE):
                    violations.append(f"{filepath}:{i}: Forbidden pattern '{pattern}' found: {line.strip()}")

            # Also check for forbidden words as whole words
            for word in FORBIDDEN_WORDS:
                if re.search(r'\b' + re.escape(word) + r'\b', line):
                    violations.append(f"{filepath}:{i}: Forbidden word '{word}' found: {line.strip()}")

    except UnicodeDecodeError:
        pass  # Skip binary files
    except Exception as e:
        print(f"Warning: Could not read {filepath}: {e}")

    return violations

def check_particles_folder(root):
    """Check for particles resource folder."""
    violations = []
    for dirpath, dirnames, filenames in os.walk(root):
        if 'particles' in dirnames:
            violations.append(f"{dirpath}/particles: Forbidden 'particles' resource folder found")
        for fname in filenames:
            if fname.endswith('.json') and 'particle' in fname.lower():
                violations.append(f"{dirpath}/{fname}: Forbidden particle JSON file")
    return violations

def main():
    src_root = 'src/main/java'
    resources_root = 'src/main/resources'

    all_violations = []

    # Check Java source files
    for root, dirs, files in os.walk(src_root):
        for f in files:
            if f.endswith('.java'):
                filepath = os.path.join(root, f)
                all_violations.extend(check_file(filepath))

    # Check resource files (JSON, etc.)
    for root, dirs, files in os.walk(resources_root):
        for f in files:
            if f.endswith('.json') or f.endswith('.lang'):
                filepath = os.path.join(root, f)
                all_violations.extend(check_file(filepath))

    # Check for particles folder
    all_violations.extend(check_particles_folder(resources_root))

    if all_violations:
        print("ZERO-PARTICLE RULE VIOLATIONS FOUND:")
        for v in all_violations:
            print(f"  {v}")
        return 1
    else:
        print("Zero-particle check PASSED: No forbidden particle references found.")
        return 0

if __name__ == '__main__':
    sys.exit(main())