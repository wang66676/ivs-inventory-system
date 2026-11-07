package com.ivs.masterdata;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * 可配置的序列号状态机模板。
 *
 * <p>产品大项通过 {@code status_template} 引用模板，因此新增产品大项时只需选择模板，
 * 不需要修改代码；需要新流程时新增一个模板即可。</p>
 */
public enum SerialStatusTemplate {

    /** 整车：采购在途 → 到店 → 在库 → 锁定 → 已售 → 已交付，可退回/维修/报废。 */
    VEHICLE(vehicleTransitions()),

    /** 动力电池：与整车同构（二期扩展在站充电、客户在用、退役）。 */
    BATTERY(batteryTransitions()),

    /** 简单序列号商品：建档 → 在库 → 已售 → 已交付。 */
    SIMPLE(simpleTransitions());

    private final Map<SerialStatus, Set<SerialStatus>> transitions;

    SerialStatusTemplate(Map<SerialStatus, Set<SerialStatus>> transitions) {
        this.transitions = transitions;
    }

    public boolean allows(SerialStatus from, SerialStatus to) {
        return transitions.getOrDefault(from, Set.of()).contains(to);
    }

    public Set<SerialStatus> nextStatuses(SerialStatus from) {
        return transitions.getOrDefault(from, Set.of());
    }

    /**
     * 求从 from 到 to 的最短合法流转路径（不含起点）。
     * 用于「收货时把车辆从计划状态推进到在库」这类需要按模板逐步流转的场景。
     */
    public List<SerialStatus> pathTo(SerialStatus from, SerialStatus to) {
        if (from == to) {
            return List.of();
        }
        Map<SerialStatus, SerialStatus> previous = new EnumMap<>(SerialStatus.class);
        java.util.Deque<SerialStatus> queue = new java.util.ArrayDeque<>();
        previous.put(from, null);
        queue.add(from);
        boolean found = false;
        while (!queue.isEmpty() && !found) {
            SerialStatus current = queue.poll();
            for (SerialStatus next : nextStatuses(current)) {
                if (previous.containsKey(next)) {
                    continue;
                }
                previous.put(next, current);
                if (next == to) {
                    found = true;
                    break;
                }
                queue.add(next);
            }
        }
        if (!found) {
            throw new IllegalStateException("状态模板 " + name() + " 不允许从 " + from + " 流转到 " + to);
        }
        java.util.LinkedList<SerialStatus> path = new java.util.LinkedList<>();
        SerialStatus cursor = to;
        while (cursor != null && cursor != from) {
            path.addFirst(cursor);
            cursor = previous.get(cursor);
        }
        return List.copyOf(path);
    }

    /** 按模板名解析，未知模板直接拒绝，避免写入拼错的状态机。 */
    public static SerialStatusTemplate requireOf(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("序列号商品必须指定状态机模板（status_template）");
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("未知的状态机模板: " + name);
        }
    }

    private static Map<SerialStatus, Set<SerialStatus>> vehicleTransitions() {
        Map<SerialStatus, Set<SerialStatus>> map = new EnumMap<>(SerialStatus.class);
        map.put(SerialStatus.PLANNED, EnumSet.of(SerialStatus.IN_TRANSIT, SerialStatus.SCRAPPED));
        map.put(SerialStatus.IN_TRANSIT, EnumSet.of(SerialStatus.ARRIVED, SerialStatus.SCRAPPED));
        map.put(SerialStatus.ARRIVED, EnumSet.of(SerialStatus.IN_STOCK, SerialStatus.SCRAPPED));
        map.put(SerialStatus.IN_STOCK, EnumSet.of(SerialStatus.LOCKED, SerialStatus.RETURNED, SerialStatus.SCRAPPED));
        map.put(SerialStatus.LOCKED, EnumSet.of(SerialStatus.SOLD, SerialStatus.IN_STOCK));
        map.put(SerialStatus.SOLD, EnumSet.of(SerialStatus.DELIVERED));
        map.put(SerialStatus.DELIVERED, EnumSet.of(SerialStatus.RETURNED));
        map.put(SerialStatus.RETURNED, EnumSet.of(SerialStatus.REPAIRING, SerialStatus.IN_STOCK, SerialStatus.SCRAPPED));
        map.put(SerialStatus.REPAIRING, EnumSet.of(SerialStatus.IN_STOCK, SerialStatus.SCRAPPED));
        map.put(SerialStatus.SCRAPPED, EnumSet.noneOf(SerialStatus.class));
        return map;
    }

    private static Map<SerialStatus, Set<SerialStatus>> simpleTransitions() {
        Map<SerialStatus, Set<SerialStatus>> map = new EnumMap<>(SerialStatus.class);
        map.put(SerialStatus.PLANNED, EnumSet.of(SerialStatus.IN_STOCK, SerialStatus.SCRAPPED));
        map.put(SerialStatus.IN_STOCK, EnumSet.of(SerialStatus.SOLD, SerialStatus.SCRAPPED));
        map.put(SerialStatus.SOLD, EnumSet.of(SerialStatus.DELIVERED));
        return map;
    }

    private static Map<SerialStatus, Set<SerialStatus>> batteryTransitions() {
        Map<SerialStatus, Set<SerialStatus>> map = new EnumMap<>(SerialStatus.class);
        map.put(SerialStatus.PLANNED, EnumSet.of(SerialStatus.IN_TRANSIT, SerialStatus.SCRAPPED));
        map.put(SerialStatus.IN_TRANSIT, EnumSet.of(SerialStatus.ARRIVED, SerialStatus.SCRAPPED));
        map.put(SerialStatus.ARRIVED, EnumSet.of(SerialStatus.IN_STOCK, SerialStatus.SCRAPPED));
        map.put(SerialStatus.IN_STOCK, EnumSet.of(SerialStatus.LOCKED, SerialStatus.IN_LEASE,
                SerialStatus.RETURNED, SerialStatus.SCRAPPED));
        map.put(SerialStatus.LOCKED, EnumSet.of(SerialStatus.SOLD, SerialStatus.IN_STOCK, SerialStatus.IN_LEASE));
        map.put(SerialStatus.SOLD, EnumSet.of(SerialStatus.DELIVERED));
        map.put(SerialStatus.DELIVERED, EnumSet.of(SerialStatus.RETURNED));
        map.put(SerialStatus.RETURNED, EnumSet.of(SerialStatus.REPAIRING, SerialStatus.IN_STOCK, SerialStatus.SCRAPPED));
        map.put(SerialStatus.REPAIRING, EnumSet.of(SerialStatus.IN_STOCK, SerialStatus.AT_STATION, SerialStatus.RETIRED));
        map.put(SerialStatus.IN_LEASE, EnumSet.of(SerialStatus.AT_STATION, SerialStatus.IN_USE,
                SerialStatus.REPAIRING, SerialStatus.RETIRED, SerialStatus.IN_STOCK));
        map.put(SerialStatus.AT_STATION, EnumSet.of(SerialStatus.IN_USE, SerialStatus.CHARGING,
                SerialStatus.REPAIRING, SerialStatus.RETIRED));
        map.put(SerialStatus.IN_USE, EnumSet.of(SerialStatus.AT_STATION, SerialStatus.REPAIRING, SerialStatus.RETIRED));
        map.put(SerialStatus.CHARGING, EnumSet.of(SerialStatus.AT_STATION, SerialStatus.IN_USE,
                SerialStatus.REPAIRING, SerialStatus.RETIRED));
        map.put(SerialStatus.RETIRED, EnumSet.of(SerialStatus.SCRAPPED));
        map.put(SerialStatus.SCRAPPED, EnumSet.noneOf(SerialStatus.class));
        return map;
    }
}
