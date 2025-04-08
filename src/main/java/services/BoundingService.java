package services;

import model.ScheduleNodeRemainingOrders;

import java.util.Map;

public class BoundingService {

    public double calcLowerBound(Map<Integer, Double> machineLoads, Map<Integer, ScheduleNodeRemainingOrders> remainingOrders, double minDurationNextStepsScheduledOrders) {
        return lbm(machineLoads, remainingOrders, minDurationNextStepsScheduledOrders);
    }

    public double lbm(Map<Integer, Double> machineLoads, Map<Integer, ScheduleNodeRemainingOrders> remainingOrders, double minDurationNextStepsScheduledOrders) {
        double act = machineLoads.values().stream().mapToDouble(i -> i).average().getAsDouble() +
                remainingOrdersThisStepDurationAvg(remainingOrders, machineLoads.size());
        double maxCt = machineLoads.values().stream().mapToDouble(i -> i).max().getAsDouble();

        return act >= maxCt ?
                act + remainingOrdersNextStepsDurationMin(remainingOrders) :
                maxCt + minDurationNextStepsScheduledOrders;
    }

//    public double lbj(Map<Integer, Double> machineLoads, Map<Integer, ScheduleNodeRemainingOrders> remainingOrders) {
//        double minCt = machineLoads.values().stream().mapToDouble(i->i).max().getAsDouble();
//
//
//    }


    private double remainingOrdersNextStepsDurationMin(Map<Integer, ScheduleNodeRemainingOrders> remainingOrders) {
        return remainingOrders.size() == 0 ? 0 :
                remainingOrders.values().stream().mapToDouble(ro -> ro.durationNextSteps).min().getAsDouble();
    }

    private double remainingOrdersThisStepDurationAvg(Map<Integer, ScheduleNodeRemainingOrders> remainingOrders, int machinesNum) {
        return remainingOrders.size() == 0 ? 0 :
                remainingOrders.values().stream().mapToDouble(ro -> ro.duration * ro.number).sum() / machinesNum;
    }

    public double act(Map<Integer, Double> machineLoads, Map<Integer, ScheduleNodeRemainingOrders> remainingOrders) {
        return machineLoads.values().stream().mapToDouble(i -> i).average().getAsDouble() +
                remainingOrdersThisStepDurationAvg(remainingOrders, machineLoads.size());
    }

}
