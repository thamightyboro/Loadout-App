#!/usr/bin/env python3
"""
Builds app/src/main/assets/components.json from the SWG-Source dsrc datatables.

For every ship component template it records the reverse-engineering level and, for each
stat, the average value and modifier the server rolls from:

    value = average * (1 + z * modifier / 2),   z = a standard bell-curve roll

(space_crafting.randBell / getBellValue). Booster max energy is the exception: it gets a
flat roll of average + uniform(-modifier, +modifier).

It also records the Space Duty token vendor lists (which items a "level N" purchase can give).

Usage: build_components.py <path to dsrc checkout> <output json>
"""
import csv
import json
import os
import sys

dsrc, out_path = sys.argv[1], sys.argv[2]
base = os.path.join(dsrc, "sku.0/sys.server/compiled/game/datatables")

COMMON = [
    ("armor", "fltMaximumArmorHitpoints", "fltMaximumArmorHitpointsMod", "bell"),
    ("drain", "fltEnergyMaintenance", "fltEnergyMaintenanceModifier", "bell"),
    ("mass", "fltMass", "fltMassModifier", "bell"),
]
BY_TYPE = {
    "armor": [],
    "weapon": [
        ("minDamage", "fltMinDamage", "fltMinDamageModifier", "bell"),
        ("maxDamage", "fltMaxDamage", "fltMaxDamageModifier", "bell"),
        ("vsShields", "fltShieldEffectiveness", "fltShieldEffectivenessModifier", "bell"),
        ("vsArmor", "fltArmorEffectiveness", "fltArmorEffectivenessModifier", "bell"),
        ("energyPerShot", "fltEnergyPerShot", "fltEnergyPerShotModifier", "bell"),
        ("refireRate", "fltRefireRate", "fltRefireRateModifier", "bell"),
    ],
    # Note: the server rolls back shields from the FRONT average with the back modifier.
    "shield": [
        ("shieldFront", "fltShieldHitpointsMaximumFront", "fltShieldHitpointsMaximumFrontModifier", "bell"),
        ("shieldBack", "fltShieldHitpointsMaximumFront", "fltShieldHitpointsMaximumBackModifier", "bell"),
        ("shieldRecharge", "fltShieldRechargeRate", "fltShieldRechargeRateModifier", "bell"),
    ],
    "reactor": [("generation", "fltEnergyGeneration", "fltEnergyGenerationModifier", "bell")],
    "engine": [
        ("speed", "fltMaxSpeed", "fltMaxSpeedModifier", "bell"),
        ("pitch", "fltMaxPitch", "fltMaxPitchModifier", "bell"),
        ("yaw", "fltMaxYaw", "fltMaxYawModifier", "bell"),
        ("roll", "fltMaxRoll", "fltMaxRollModifier", "bell"),
    ],
    "capacitor": [
        ("capEnergy", "fltMaxEnergy", "fltMaxEnergyModifier", "bell"),
        ("capRecharge", "fltRechargeRate", "fltRechargeRateModifier", "bell"),
    ],
    "booster": [
        ("boosterEnergy", "fltMaximumEnergy", "fltMaximumEnergyModifier", "uniform"),
        ("boosterRecharge", "fltRechargeRate", "fltRechargeRateModifier", "bell"),
        ("consumption", "fltConsumptionRate", "fltConsumptionRateModifier", "bell"),
        ("acceleration", "fltAcceleration", "fltAccelerationModifier", "bell"),
        ("boosterSpeed", "fltMaxSpeed", "fltMaxSpeedModifier", "bell"),
    ],
    "droid_interface": [("commandSpeed", "fltCommandSpeed", "fltCommandSpeedModifier", "bell")],
}
# vendor file name -> component type
VENDOR = {"armor": "armor", "booster": "booster", "droid_interface": "droid_interface", "engine": "engine",
          "reactor": "reactor", "shield": "shield", "weap_cap": "capacitor", "weapon": "weapon"}


def read_tab(path):
    with open(path, newline="") as f:
        return list(csv.reader(f, delimiter="\t"))


def stem(template):
    return os.path.basename(template)[: -len(".iff")]


items = []
for t, extra in BY_TYPE.items():
    rows = read_tab(os.path.join(base, "ship/components", t + ".tab"))
    header = rows[0]
    for r in rows[2:]:
        if not r or not r[0].startswith("object/"):
            continue
        d = dict(zip(header, r))
        stats = {}
        for key, avg_col, mod_col, kind in COMMON + extra:
            try:
                entry = [float(d[avg_col]), float(d[mod_col])]
            except (KeyError, ValueError):
                continue
            if kind == "uniform":
                entry.append("u")
            stats[key] = entry
        re_level = (d.get("reverseEngineeringLevel") or "0").strip() or "0"
        items.append({"id": stem(r[0]), "type": t, "re": int(float(re_level)), "s": stats})

vendor = {}
for fname, t in VENDOR.items():
    rows = read_tab(os.path.join(base, "item/vendor/space_duty", fname + ".tab"))
    header = rows[0]
    vendor[t] = {
        col.replace("level", ""): [stem(r[i]) for r in rows[2:] if len(r) > i and r[i].strip()]
        for i, col in enumerate(header)
    }

known = {i["id"] for i in items}
missing = sorted({x for lv in vendor.values() for lst in lv.values() for x in lst if x not in known})
if missing:
    print("warning: vendor items with no stats row:", missing)

os.makedirs(os.path.dirname(out_path), exist_ok=True)
with open(out_path, "w") as f:
    json.dump({
        "source": "SWG-Source/dsrc datatables/ship/components + item/vendor/space_duty",
        # trial.getSpaceDutyTokenPrice(level) = 50 + 5 * level
        "price": {"base": 50, "perLevel": 5},
        "items": items,
        "vendor": vendor,
    }, f, separators=(",", ":"))
print(f"wrote {len(items)} components to {out_path}")
