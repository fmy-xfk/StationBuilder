package cn.myfrank.stationbuilder.utils;

public enum CatenaryType {
    None(0),
    Vanilla(1),
    MSD(2),
    CEE(3),
    CPW(4);

    private final int value;

    CatenaryType(int value) {
        this.value = value;
    }

    public CatenaryType fromValue(int value) {
        return switch (value) {
            case 0 -> None;
            case 1 -> Vanilla;
            case 2 -> MSD;
            case 3 -> CEE;
            case 4 -> CPW;
            default -> throw new IllegalStateException("Unexpected value: " + value);
        };
    }

    public int getValue() {
        return value;
    }

    public String getName() {
        return switch(this) {
            case None -> "none";
            case Vanilla -> "vanilla";
            case MSD -> "msd";
            case CEE -> "cee";
            case CPW -> "cpw";
        };
    }

    public static CatenaryType getDefaultType() {
        if (CommonUtil.isMsdLoaded()) {
            return MSD;
        } else if (CommonUtil.isCeeLoaded()) {
            return CEE;
        } else if (CommonUtil.isCpwLoaded()) {
            return CPW;
        } else {
            return Vanilla;
        }
    }
}