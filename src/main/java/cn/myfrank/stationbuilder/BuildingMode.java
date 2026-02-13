package cn.myfrank.stationbuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BuildingMode {
    public enum Up {
        Clear((byte) 0),
        Tunnel((byte) 1);
        private final byte value;

        Up(byte value) {
            this.value = value;
        }

        public byte getValue() {
            return value;
        }

        public static Up fromValue(byte value) {
            return switch (value) {
                case 0 -> Clear;
                case 1 -> Tunnel;
                default -> throw new IllegalArgumentException("Invalid direction value: " + value);
            };
        }
    }

    public enum Down {
        Ballast((byte) 0),
        ThickBallast((byte) 1),
        Bridge((byte) 2);

        private final byte value;
        Down(byte value) {
            this.value = value;
        }

        public byte getValue() {
            return value;
        }

        public static Down fromValue(byte value) {
            return switch (value) {
                case 0 -> Ballast;
                case 1 -> ThickBallast;
                case 2 -> Bridge;
                default -> throw new IllegalArgumentException("Invalid direction value: " + value);
            };
        }
    }

    public static byte[] smoothModes(byte[][] modes) {
        int cols = modes[0].length;
        if (cols < 10) {
            return smoothModes(modes, 3, 0.5);
        } else if (cols < 20) {
            return smoothModes(modes, 5, 0.5);
        } else if (cols < 50) {
            return smoothModes(modes, 7, 0.5);
        } else if (cols < 100) {
            return smoothModes(modes, 11, 0.5);
        } else {
            return smoothModes(modes, 15, 0.5);
        }
    }

    public static byte[] smoothModes(byte[][] modes, int windowSize, double threshold) {
        int m = modes.length;  // 行数（样本数）
        int n = modes[0].length;  // 列数（时间/位置）

        if (m == 0 || n == 0) return null;

        // 使用更保守的默认窗口大小
        if (windowSize < 3) windowSize = 3;
        if (windowSize % 2 == 0) windowSize++;
        int halfWindow = windowSize / 2;

        byte[] result = new byte[n];

        for (int j = 0; j < n; j++) {
            // 考虑跨行和时间的二维区域
            List<Byte> candidates = new ArrayList<>();

            // 收集当前列为中心的二维窗口数据
            for (int colOffset = -halfWindow; colOffset <= halfWindow; colOffset++) {
                int actualCol = j + colOffset;
                if (actualCol < 0 || actualCol >= n) continue;

                // 对该列的所有行进行采样
                for (byte[] mode : modes) {
                    candidates.add(mode[actualCol]);
                }
            }

            // 统计频率
            Map<Byte, Integer> freqMap = new HashMap<>();
            for (byte value : candidates) {
                freqMap.put(value, freqMap.getOrDefault(value, 0) + 1);
            }

            // 找到众数
            Byte majority = null;
            int maxCount = 0;
            int totalCount = candidates.size();

            for (Map.Entry<Byte, Integer> entry : freqMap.entrySet()) {
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    majority = entry.getKey();
                }
            }

            // 检查阈值
            double majorityRatio = (double) maxCount / totalCount;
            if (majorityRatio >= threshold) {
                result[j] = majority;
            } else {
                // 如果达不到阈值，使用当前列的简单众数
                Map<Byte, Integer> colFreq = new HashMap<>();
                for (byte[] mode : modes) {
                    byte value = mode[j];
                    colFreq.put(value, colFreq.getOrDefault(value, 0) + 1);
                }

                Byte colMajority = null;
                int colMax = 0;
                for (Map.Entry<Byte, Integer> entry : colFreq.entrySet()) {
                    if (entry.getValue() > colMax) {
                        colMax = entry.getValue();
                        colMajority = entry.getKey();
                    }
                }
                if (colMajority != null) {
                    result[j] = colMajority;
                }
            }
        }
        return result;
    }
}
