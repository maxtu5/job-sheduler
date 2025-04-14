package model.solutiontree;

import lombok.AllArgsConstructor;

import java.util.Map;

@AllArgsConstructor
public class ScheduleNode {

    public ScheduleNodeType type;

    public int index;

    public Map<Integer, ScheduleNodeRemainingOrders> remainingOrders;

    public Map<Integer, Double> machinesLoad;

    public double lowerBound;

    public double minDurationNextStepsScheduledOrders;

}
