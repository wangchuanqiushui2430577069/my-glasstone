import numpy as np
import matplotlib.pyplot as plt
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from glasstone.overpressure import brode_overpressure_distances


TARGET_OPS = np.array([2.0, 3.0, 3.8, 4.9, 7.1, 8.1, 10.0, 12.0], dtype=float)
YIELD_KT = 20.0
BURST_HEIGHT_M = 300.0
POINTS_PER_RING = 361


def circle_points(radius, point_count=POINTS_PER_RING):
    angles = np.linspace(0.0, 2.0 * np.pi, point_count)
    return radius * np.cos(angles), radius * np.sin(angles)

# 对应glasstone-java下
#    OverPressureAPITest测试类的
#      matchesScalarBrodeInverseForExplicitUnits测试方法
# 导出的图片位于target下的test-artifacts包下的overpressure-api包

def main():
    radii_m = brode_overpressure_distances(
        TARGET_OPS,
        YIELD_KT,
        BURST_HEIGHT_M,
        n=120,
        yunits="kT",
        dunits="m",
        opunits="kg/cm^2",
    )

    plt.style.use("dark_background")
    fig, ax = plt.subplots(figsize=(9, 9))
    fig.patch.set_facecolor("#101218")
    ax.set_facecolor("#101218")

    colors = [
        "#1E90FF",
        "#17BECF",
        "#FFBF00",
        "#2CA02C",
        "#C2185B",
        "#D62728",
        "#FF1493",
        "#F5F7FA",
    ]

    for index, (target_op, radius_m) in enumerate(zip(TARGET_OPS, radii_m)):
        xs, ys = circle_points(radius_m)
        ax.plot(xs, ys, lw=2, color=colors[index], label=f"{target_op:g} kg/cm^2")
        ax.text(
            radius_m,
            0.0,
            f" {target_op:g}",
            color=colors[index],
            va="center",
            ha="left",
            fontsize=10,
        )

    ax.scatter([0.0], [0.0], color="white", marker="D", s=55, label="ground zero")

    axis_limit = float(np.max(radii_m) * 1.15)
    ax.set_xlim(-axis_limit, axis_limit)
    ax.set_ylim(-axis_limit, axis_limit)
    ax.set_aspect("equal", adjustable="box")
    ax.grid(color="#444A57", alpha=0.5)

    ax.set_title("Brode Overpressure Rings\nYield: 20 kT | Burst height: 300 m", fontsize=14)
    ax.set_xlabel("x (m)")
    ax.set_ylabel("y (m)")
    ax.legend(loc="upper right", framealpha=0.85, facecolor="#171A22", edgecolor="#707785")

    plt.show()


if __name__ == "__main__":
    main()
