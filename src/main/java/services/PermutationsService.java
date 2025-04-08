package services;

import model.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PermutationsService {

    private final double HOURS_PER_DAY = 24;
    private final Map<Integer, OrderType> orderTypes;
    private final double workhoursPerDay;
    private final BoundingService boundingService;
    private final boolean doBound = true;

    public PermutationsService(Map<Integer, OrderType> orderTypes, double workhoursPerDay) {
        this.orderTypes = orderTypes;
        this.workhoursPerDay = workhoursPerDay;
        boundingService = new BoundingService();
    }

    public List<List<ScheduleNode>> generateAllSchedules(Map<StepType, Integer> machineNums,
                                                         Map<Integer, Integer> orderNumsByTypes) {

        Map<Integer, ScheduleNodeRemainingOrders> remainingOrdersByTypes = orderNumsByTypes.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        me -> new ScheduleNodeRemainingOrders(
                                me.getValue(),
                                durationForStep(me.getKey(), StepType.values()[0]),
                                durationForNextSteps(me.getKey(), StepType.values()[0]))));

        Map<StepType, List<List<ScheduleNode>>> allSchedulesPerOperation = buildAllSchedulesForAllSteps(machineNums, remainingOrdersByTypes);

        Map<Integer, List<List<ScheduleNode>>> allSchedules = new HashMap<>();
        int step = 0;
        while (allSchedulesPerOperation.get(StepType.values()[step]).isEmpty()) step++;
        allSchedules.put(step + 1, allSchedulesPerOperation.get(StepType.values()[step]));

        while (allSchedules.entrySet().stream().filter(me -> me.getKey() < StepType.values().length).anyMatch(me -> me.getValue().size() > 0)) {

            int level = StepType.values().length - 1;
            while (level > step + 1 && (!allSchedules.containsKey(level) || allSchedules.get(level).isEmpty()))
                level--;
            int newLevel = level + 1;
            while (newLevel < StepType.values().length && allSchedulesPerOperation.get(StepType.values()[newLevel - 1]).isEmpty())
                newLevel++;

            List<ScheduleNode> prefix = allSchedules.get(level).remove(allSchedules.get(level).size() - 1);
            for (List<ScheduleNode> suffix : allSchedulesPerOperation.get(StepType.values()[newLevel - 1])) {
                List<ScheduleNode> newSchedule = mergeToSchedule(prefix, suffix);
                if (!allSchedules.containsKey(newLevel)) allSchedules.put(newLevel, new ArrayList<>());
                allSchedules.get(newLevel).add(newSchedule);
            }
        }
        return allSchedules.get(StepType.values().length);
    }

    private Map<StepType, List<List<ScheduleNode>>> buildAllSchedulesForAllSteps(Map<StepType, Integer> machineNums, Map<Integer, ScheduleNodeRemainingOrders> remainingOrdersByTypes) {
        return Arrays.stream(StepType.values())
                .collect(Collectors.toMap(
                        operationType -> operationType,
                        operationType -> buildAllSchedulesForStep(
                                operationType,
                                machineNums.get(operationType),
                                remainingOrdersForStep(remainingOrdersByTypes, operationType))));
    }

    private List<List<ScheduleNode>> buildAllSchedulesForStep(StepType stepType, int machineNums, Map<Integer, ScheduleNodeRemainingOrders> ordersByTypes) {

        int totalOrders = ordersByTypes.values().stream().mapToInt(i -> i.number).sum();
        int totalTypes = ordersByTypes.size();
        int startNodes = Math.max(1, totalTypes - machineNums + 1);
        double bestLowerBound = Double.MAX_VALUE;
        List<List<ScheduleNode>> bestSchedules = new ArrayList<>();

        LinkedList<List<ScheduleNode>> allSchedules = buildInitialNodes(ordersByTypes, machineNums, startNodes, stepType);

        while (allSchedules.stream().mapToInt(List::size).sum() > 0) {

            if (allSchedules.peekLast().size() == totalOrders) {

                List<ScheduleNode> schedule = allSchedules.pollLast();
                double scheduleLowerBound = schedule.get(schedule.size() - 1).lowerBound;

                if (scheduleLowerBound < bestLowerBound) {
                    bestLowerBound = scheduleLowerBound;
                    bestSchedules = new ArrayList<>();
                    bestSchedules.add(schedule);
                } else if (scheduleLowerBound == bestLowerBound)
                    bestSchedules.add(schedule);

            } else {

                List<ScheduleNode> prefix = allSchedules.pollLast();

                if (prefix.get(prefix.size() - 1).lowerBound <= bestLowerBound) {
                    List<ScheduleNode> possibleNodes = createBranchingNodes(prefix, Math.min(totalTypes, machineNums), stepType, bestLowerBound);
                    for (ScheduleNode newNode : possibleNodes) {
                        if (!doBound || newNode.lowerBound <= bestLowerBound) {
                            allSchedules.add(mergeToSchedule(prefix, newNode));
                        }
                    }
                }
            }
        }
//        System.out.println("Built schedules for step " +stepType + ", " + bestSchedules.size());
        return bestSchedules;
    }

    private LinkedList<List<ScheduleNode>> buildInitialNodes(Map<Integer, ScheduleNodeRemainingOrders> ordersByTypes, int machineNums, int startNodes, StepType stepType) {
        return ordersByTypes.keySet().stream()
                .sorted().limit(startNodes).mapToInt(i -> i)
                .mapToObj(i -> {
                    Map<Integer, ScheduleNodeRemainingOrders> newRemainingOrders =
                            copyRemainingOrdersMinusOrder(ordersByTypes, i);
                    Map<Integer, Double> newMachineLoads = buildInitialMachineLoads(
                            machineNums,
                            orderTypes.get(i).steps.stream()
                                    .filter(o -> o.getStepType().equals(stepType))
                                    .findAny().orElse(null).getDuration());
                    return new ScheduleNode(
                            ScheduleNodeType.SQUARE,
                            i,
                            newRemainingOrders,
                            newMachineLoads,
                            boundingService.calcLowerBound(newMachineLoads, newRemainingOrders, ordersByTypes.get(i).durationNextSteps),
                            ordersByTypes.get(i).durationNextSteps);
                })
                .map(Arrays::asList).collect(Collectors.toCollection(LinkedList::new));
    }

    private List<ScheduleNode> createBranchingNodes(List<ScheduleNode> prefix, int requiredSquares, StepType stepType, double lowerBoundMax) {

        List<ScheduleNode> branchingNodes = new ArrayList<>();
        ScheduleNode lastPrefixNode = prefix.get(prefix.size() - 1);

        for (Integer index : lastPrefixNode.remainingOrders.keySet()) {
            int squaresSoFar = countSquares(prefix);
            int lastSquareIndex = findLastSquareIndex(prefix);
            double duration = orderTypes.get(index).steps.stream()
                    .filter(o -> o.getStepType().equals(stepType))
                    .findAny().orElse(null).getDuration();

            Map<Integer, ScheduleNodeRemainingOrders> newRemainingOrders = copyRemainingOrdersMinusOrder(
                    lastPrefixNode.remainingOrders,
                    index);
            Map<Integer, Double> newMachineLoads = copyMachineLoadsPlusOrder(
                    lastPrefixNode.machinesLoad,
                    squaresSoFar,
                    duration
            );

            double newMinDurationNextStepsScheduledOrders = Math.min(
                    lastPrefixNode.minDurationNextStepsScheduledOrders,
                    newRemainingOrders.size() == 0 ? 0 : newRemainingOrders.values().stream().mapToDouble(o -> o.durationNextSteps).min().getAsDouble());

            double lowerBound = boundingService.calcLowerBound(
                    newMachineLoads,
                    newRemainingOrders,
                    newMinDurationNextStepsScheduledOrders);

            boolean squareAdded = false;
            if (lowerBound <= lowerBoundMax && squaresSoFar < requiredSquares && index >= lastSquareIndex) {
                branchingNodes.add(new ScheduleNode(ScheduleNodeType.SQUARE, index, newRemainingOrders, newMachineLoads, lowerBound, newMinDurationNextStepsScheduledOrders));
                squareAdded = true;
            }

            int availableToSquareBranch = newRemainingOrders.entrySet().stream()
                    .filter(me -> me.getKey() >= lastSquareIndex)
                    .map(Map.Entry::getValue)
                    .mapToInt(o -> o.number)
                    .sum();

            if (!squareAdded || availableToSquareBranch >= requiredSquares - squaresSoFar) {
//                if (squareAdded) newRemainingOrders = copyRemainingOrdersMinusOrder(lastPrefixNode.remainingOrders, index);
                newMachineLoads = copyMachineLoadsPlusOrder(
                        lastPrefixNode.machinesLoad,
                        squaresSoFar - 1,
                        duration
                );
                lowerBound = boundingService.calcLowerBound(newMachineLoads, newRemainingOrders, newMinDurationNextStepsScheduledOrders);
                if (lowerBound <= lowerBoundMax)
                    branchingNodes.add(new ScheduleNode(ScheduleNodeType.ROUND, index, newRemainingOrders, newMachineLoads, lowerBound, newMinDurationNextStepsScheduledOrders));
            }
        }
        return branchingNodes;
    }

    private Map<Integer, ScheduleNodeRemainingOrders> copyRemainingOrdersMinusOrder(Map<Integer, ScheduleNodeRemainingOrders> orderNumsByTypes, Integer i) {
        Map<Integer, ScheduleNodeRemainingOrders> retval = new HashMap<>();
        for (Integer key : orderNumsByTypes.keySet()) {
            if (key.equals(i)) {
                if (orderNumsByTypes.get(key).number > 1)
                    retval.put(key, new ScheduleNodeRemainingOrders(
                            orderNumsByTypes.get(key).number - 1,
                            orderNumsByTypes.get(key).duration,
                            orderNumsByTypes.get(key).durationNextSteps));
            } else retval.put(key, orderNumsByTypes.get(key));
        }
        return retval;
    }

    private Map<Integer, Double> copyMachineLoadsPlusOrder(Map<Integer, Double> machinesLoad, int machineIndex, double duration) {
        return machinesLoad.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        me -> me.getValue() + (me.getKey() == machineIndex ? hoursToAdd(me.getValue(), duration) : 0)
                ));
    }

    private double durationForStep(Integer orderTypeId, StepType stepType) {
        return orderTypes.get(orderTypeId).steps.stream()
                .filter(o -> o.getStepType().equals(stepType))
                .mapToDouble(Step::getDuration)
                .findAny()
                .orElse(0);
    }

    private double durationForNextSteps(Integer orderTypeId, StepType stepType) {
        return orderTypes.get(orderTypeId).steps.stream()
                .filter(o -> o.getStepType().ordinal() > stepType.ordinal())
                .mapToDouble(Step::getDuration)
                .sum();
    }

    private Map<Integer, ScheduleNodeRemainingOrders> remainingOrdersForStep(
            Map<Integer, ScheduleNodeRemainingOrders> remainingOrders,
            StepType stepType) {

        return remainingOrders.entrySet().stream()
                .filter(me -> orderTypes.get(me.getKey()).steps.stream().anyMatch(step -> step.getStepType().equals(stepType)))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        me -> new ScheduleNodeRemainingOrders(
                                me.getValue().number,
                                durationForStep(me.getKey(), stepType),
                                durationForNextSteps(me.getKey(), stepType))));
    }

    private Map<Integer, Double> buildInitialMachineLoads(int machinesNum, double duration) {
        return IntStream.range(0, machinesNum).boxed().collect(Collectors.toMap(
                i -> i,
                i -> i == 0 ? duration : 0));
    }

    private double hoursToAdd(double value, double duration) {
        double scheduledLastDay = value % HOURS_PER_DAY;
        return duration + (scheduledLastDay + duration <= workhoursPerDay ? 0 : (HOURS_PER_DAY - scheduledLastDay));
    }

    private int findLastSquareIndex(List<ScheduleNode> schedule) {
        return schedule.stream().filter(scheduleNode -> scheduleNode.type.equals(ScheduleNodeType.SQUARE))
                .reduce((first, second) -> second)
                .orElse(null)
                .index;
    }

    private int countSquares(List<ScheduleNode> currentSchedule) {
        return (int) currentSchedule.stream().filter(s -> s.type.equals(ScheduleNodeType.SQUARE)).count();
    }

    private List<ScheduleNode> mergeToSchedule(List<ScheduleNode> currentSchedule, ScheduleNode node) {
        List<ScheduleNode> newSchedule = new ArrayList<>(currentSchedule);
        newSchedule.get(newSchedule.size() - 1).remainingOrders = null;
        newSchedule.add(node);
        return newSchedule;
    }

    private List<ScheduleNode> mergeToSchedule(List<ScheduleNode> currentSchedule, List<ScheduleNode> nodes) {
        List<ScheduleNode> newSchedule = new ArrayList<>(currentSchedule);
        newSchedule.addAll(nodes);
        return newSchedule;
    }

}
