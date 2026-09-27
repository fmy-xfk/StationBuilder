package cn.myfrank.stationbuilder;

import net.minecraft.util.math.BlockPos;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class RailPreviewCache {
    // 内部记录类
    // 键类，用于作为HashMap的键
    private record CacheKey(BlockPos start, float startAngle, BlockPos end, float endAngle) {

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheKey cacheKey = (CacheKey) o;
            return Float.compare(cacheKey.startAngle, startAngle) == 0 &&
                    Float.compare(cacheKey.endAngle, endAngle) == 0 &&
                    Objects.equals(start, cacheKey.start) &&
                    Objects.equals(end, cacheKey.end);
        }

    }

    // FIFO缓存实现 - 使用插入顺序，而不是访问顺序
    private final Map<CacheKey, TestConnectResult> cache;

    // 默认最大条目数
    private static final int DEFAULT_MAX_ENTRIES = 100;
    private final int maxEntries;

    /**
     * 使用默认配置创建缓存（最大100100条）
     */
    public RailPreviewCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    /**
     * 使用自定义配置创建缓存
     * @param maxEntries 最大条目数
     */
    public RailPreviewCache(int maxEntries) {
        this.maxEntries = maxEntries;

        // 创建LinkedHashMap，设置accessOrder为false以保持插入顺序
        // 当超过最大条目数时自动移除最先插入的条目
        this.cache = new LinkedHashMap<CacheKey, TestConnectResult>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, TestConnectResult> eldest) {
                return size() > maxEntries;
            }
        };
    }

    /**
     * 插入缓存记录
     * @param start 起点位置
     * @param startAngle 起点角度
     * @param end 终点位置
     * @param endAngle 终点角度
     * @param result 测试连接结果
     */
    public void put(BlockPos start, float startAngle, BlockPos end, float endAngle, final TestConnectResult result) {
        CacheKey key = new CacheKey(start, startAngle, end, endAngle);
        cache.put(key, result);
    }

    /**
     * 查询并获取测试连接结果
     * 注意：访问不会改变条目的顺序
     * @param start 起点位置
     * @param startAngle 起点角度
     * @param end 终点位置
     * @param endAngle 终点角度
     * @return 测试连接结果，如果不存在则返回null
     */
    public TestConnectResult get(BlockPos start, float startAngle, BlockPos end, float endAngle) {
        CacheKey key = new CacheKey(start, startAngle, end, endAngle);
        return cache.get(key);
    }

    /**
     * 检查是否存在缓存
     * 注意：访问不会改变条目的顺序
     * @param start 起点位置
     * @param startAngle 起点角度
     * @param end 终点位置
     * @param endAngle 终点角度
     * @return 是否存在缓存
     */
    public boolean contains(BlockPos start, float startAngle, BlockPos end, float endAngle) {
        CacheKey key = new CacheKey(start, startAngle, end, endAngle);
        return cache.containsKey(key);
    }

    /**
     * 清除所有缓存
     */
    public void clear() {
        cache.clear();
    }

    /**
     * 移除指定缓存
     * @param start 起点位置
     * @param startAngle 起点角度
     * @param end 终点位置
     * @param endAngle 终点角度
     */
    public void remove(BlockPos start, float startAngle, BlockPos end, float endAngle) {
        CacheKey key = new CacheKey(start, startAngle, end, endAngle);
        cache.remove(key);
    }

    /**
     * 获取缓存大小
     * @return 缓存中的记录数量
     */
    public int size() {
        return cache.size();
    }

    /**
     * 检查缓存是否为空
     * @return 缓存是否为空
     */
    public boolean isEmpty() {
        return cache.isEmpty();
    }

    /**
     * 获取最大条目数
     * @return 最大条目数
     */
    public int getMaxEntries() {
        return maxEntries;
    }

    /**
     * 手动触发清理（移除最早插入的条目直到达到指定数量）
     */
    public void trimToSize(int size) {
        if (size < 0 || size >= cache.size()) {
            return;
        }

        // 移除最早插入的条目（LinkedHashMap的第一个条目）
        while (cache.size() > size) {
            // 获取第一个（最早插入的）条目
            CacheKey firstKey = cache.keySet().iterator().next();
            cache.remove(firstKey);
        }
    }

    /**
     * 检查缓存是否已满
     * @return 是否已满
     */
    public boolean isFull() {
        return cache.size() >= maxEntries;
    }

    /**
     * 获取最早插入的条目
     * @return 最早插入的TestConnectResult，如果缓存为空则返回null
     */
    public TestConnectResult getOldest() {
        if (cache.isEmpty()) {
            return null;
        }

        // LinkedHashMap的第一个条目是最早插入的
        return cache.values().iterator().next();
    }

    /**
     * 获取最晚插入的条目
     * @return 最晚插入的TestConnectResult，如果缓存为空则返回null
     */
    public TestConnectResult getNewest() {
        if (cache.isEmpty()) {
            return null;
        }

        // 遍历到最后一个条目
        java.util.Iterator<TestConnectResult> iterator = cache.values().iterator();
        TestConnectResult last = null;
        while (iterator.hasNext()) {
            last = iterator.next();
        }
        return last;
    }

    /**
     * 移除最早插入的条目
     * @return 被移除的条目，如果缓存为空则返回null
     */
    public TestConnectResult removeOldest() {
        if (cache.isEmpty()) {
            return null;
        }

        // 获取第一个（最早插入的）键
        CacheKey firstKey = cache.keySet().iterator().next();
        return cache.remove(firstKey);
    }

    /**
     * 获取缓存统计信息
     * @return 统计信息字符串
     */
    public String getStats() {
        return String.format("缓存统计: 当前条目数=%d, 最大条目数=%d, 使用率=%.1f%%",
                size(), maxEntries, (size() * 100.0 / maxEntries));
    }

    /**
     * 获取缓存中的所有键（按插入顺序）
     * @return 按插入顺序排列的键列表
     */
    public java.util.List<CacheKey> getKeysInOrder() {
        return new java.util.ArrayList<>(cache.keySet());
    }

    /**
     * 获取缓存中的所有值（按插入顺序）
     * @return 按插入顺序排列的值列表
     */
    public java.util.List<TestConnectResult> getValuesInOrder() {
        return new java.util.ArrayList<>(cache.values());
    }
}