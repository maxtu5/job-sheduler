package services;

import builders.DataStructBuilders;
import com.fasterxml.jackson.core.JsonProcessingException;
import model.*;
import model.dto.OrderDto;
import model.dto.StationDto;
import model.report.ReportTemplate;
import model.solutiontree.ScheduleNode;
import model.solutiontree.ScheduleNodeType;
import utils.DateTimeUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.temporal.ChronoUnit.HOURS;

public class OrderSchedulerService {

    private final int maxOrdersInPortion;
    private final LocalTime workdayFrom;
    private final LocalTime workdayTo;

    private final Map<Integer, OrderType> orderTypes;
    private final List<StationDto> stationsDtos;
    private final LocalDate firstDate;

    LocalDateTime earliestEndTime = null;

    private final SolutionTreeService solutionTreeService;

    public OrderSchedulerService(List<StationDto> stationsDtos, Map<Integer, OrderType> orderTypes, LocalDate firstDate, LocalTime workdayFrom, LocalTime workdayTo, int maxOrdersInPortion) {
        this.orderTypes = orderTypes;
        this.stationsDtos = stationsDtos;
        this.firstDate = firstDate == null ? LocalDate.now().plusDays(1) : firstDate;
        this.workdayFrom = workdayFrom;
        this.workdayTo = workdayTo;
        this.maxOrdersInPortion = maxOrdersInPortion;
        solutionTreeService = new SolutionTreeService(orderTypes, HOURS.between(workdayFrom, workdayTo));
    }

    public List<ReportTemplate> scheduleOrdersA(List<OrderDto> orderDtos, int... portions) throws JsonProcessingException {

        int ordersDone = 0;
        List<List<OrderDto>> ordersByPortions = new ArrayList<>();
        List<List<List<ScheduleNode>>> scheduleListsByPortions = new ArrayList<>();

        for (int portion : portions) {
            List<OrderDto> subList = orderDtos.subList(ordersDone, ordersDone + portion);
            ordersByPortions.add(subList);
            scheduleListsByPortions.add(scheduleBatchOfOrders(subList));
            ordersDone += portion;
        }

        Map<StepType, List<Machine>> machines = DataStructBuilders.buildMachinesFromStations(stationsDtos, this.firstDate);

        for (int i = 0; i < scheduleListsByPortions.size(); i++) {
            proceedSchedule(scheduleListsByPortions.get(i).get(0), ordersByPortions.get(i), machines);
        }

        return Arrays.asList(createReport(machines, orderDtos));
    }

    public List<ReportTemplate> scheduleOrders(List<OrderDto> orderDtos) throws JsonProcessingException {

        int ordersDone = 0;
        List<List<OrderDto>> ordersByPortions = new ArrayList<>();
        List<List<List<ScheduleNode>>> scheduleListsByPortions = new ArrayList<>();

        for (int i = 0; i <= orderDtos.size() / maxOrdersInPortion; i++) {
            int lastIndex = i == orderDtos.size() / maxOrdersInPortion ? orderDtos.size() : ordersDone + maxOrdersInPortion;
            List<OrderDto> subList = orderDtos.subList(ordersDone, lastIndex);
            ordersByPortions.add(subList);
            scheduleListsByPortions.add(scheduleBatchOfOrders(subList));
            ordersDone += maxOrdersInPortion;
        }

        Map<StepType, List<Machine>> machines = DataStructBuilders.buildMachinesFromStations(stationsDtos, this.firstDate);

        for (int i = 0; i < scheduleListsByPortions.size(); i++) {
            proceedSchedule(scheduleListsByPortions.get(i).get(0), ordersByPortions.get(i), machines);
        }

        return Arrays.asList(createReport(machines, orderDtos));
    }

    public List<List<ScheduleNode>> scheduleBatchOfOrders(List<OrderDto> orderDtos) {

        Map<Integer, Integer> orderNumsByTypeIds = orderDtos.stream()
                .map(OrderDto::getType).collect(Collectors.groupingBy(t -> t, Collectors.counting()))
                .entrySet().stream()
                .collect(Collectors.toMap(me -> DataStructBuilders.typeIdByName(orderTypes, me.getKey()),
                        me -> me.getValue().intValue()));

        Map<StepType, List<Machine>> machines = DataStructBuilders.buildMachinesFromStations(stationsDtos, this.firstDate);

        List<List<ScheduleNode>> allSchedules = solutionTreeService.generateAllSchedules(
                machines.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())),
                orderNumsByTypeIds);
        allSchedules = refineSchedules(allSchedules, orderDtos);
        earliestEndTime = proceedSchedule(allSchedules.get(0), orderDtos, machines);

        return allSchedules;
    }

    private List<List<ScheduleNode>> refineSchedules(List<List<ScheduleNode>> allSchedules, List<OrderDto> orderDtos) {

        Map<StepType, List<Machine>> machines = DataStructBuilders.buildMachinesFromStations(stationsDtos, this.firstDate);

        List<List<ScheduleNode>> newSchedules = new ArrayList<>();
        LocalDateTime endTime = null;
        newSchedules.add(allSchedules.get(0));
        for (List<ScheduleNode> allSchedule : allSchedules) {
            LocalDateTime newEndTime = proceedSchedule(allSchedule, orderDtos, machines);
            if (endTime == null) {
                endTime = newEndTime;
                newSchedules.add(allSchedule);
            } else if (newEndTime.isBefore(endTime)) {
                newSchedules = new ArrayList<>();
                endTime = newEndTime;
                newSchedules.add(allSchedule);
            } else if (endTime.equals(earliestEndTime)) newSchedules.add(allSchedule);
        }
        return newSchedules;
    }

    private LocalDateTime proceedSchedule(List<ScheduleNode> schedule, List<OrderDto> orderDtos, Map<StepType, List<Machine>> machines) {

        Map<Integer, Map<String, Stack<WorkUnit>>> ordersForMachines =
                DataStructBuilders.buildWorkUnitsMapFromOrders(orderDtos, orderTypes);

        addScheduleToMachines(schedule, ordersForMachines, machines);
        removeEmptyDatesForMachines(machines);
        return ReportService.calcEndTime(machines);
    }

    private void removeEmptyDatesForMachines(Map<StepType, List<Machine>> machines) {
        for (List<Machine> list : machines.values()) {
            for (Machine machine : list) {
                List<LocalDate> emptyDates = machine.getPlan().entrySet().stream()
                        .filter(me -> me.getValue().isEmpty())
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());
                emptyDates.forEach(machine.getPlan()::remove);
            }
        }
    }

    private void addScheduleToMachines(List<ScheduleNode> schedule, Map<Integer, Map<String, Stack<WorkUnit>>> orders, Map<StepType, List<Machine>> machines) {
        Map<String, LocalDateTime> currentFinish = new HashMap<>();
        int counter = 0;
        for (StepType currentOperation : StepType.values()) {
            Map<Integer, Map<String, Stack<WorkUnit>>> ordersForOperation = orders.entrySet().stream()
                    .filter(me -> orderTypes.get(me.getKey()).steps.stream()
                            .anyMatch(o -> o.getStepType().equals(currentOperation)))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            int numOrdersForOperation = ordersForOperation.values().stream().mapToInt(Map::size).sum();
            int machine = -1;
            for (int i = 0; i < numOrdersForOperation; i++) {
                ScheduleNode step = schedule.get(counter);

                Stack<WorkUnit> stack = ordersForOperation.get(step.index).values().stream().max(Comparator.comparing(Stack::size)).orElse(null);
                WorkUnit workUnit = stack.pop();
                if (schedule.get(counter).type.equals(ScheduleNodeType.SQUARE)) machine++;
                LocalDateTime newFinish = addWorkUnitToMachine(
                        machines.get(currentOperation).get(machine), workUnit, currentFinish.get(workUnit.getOrderId()));
                currentFinish.put(workUnit.getOrderId(), newFinish);
                counter++;
            }
        }
    }

    private LocalDateTime addWorkUnitToMachine(Machine machine, WorkUnit workUnit, LocalDateTime earliestDateTime) {

        LocalDate planDate = earliestDateTime == null ? firstDate : earliestDateTime.toLocalDate();
        LocalTime earliestTime = earliestDateTime == null ? workdayFrom : earliestDateTime.toLocalTime();
        if (!machine.getPlan().containsKey(planDate))
            openNewDateForMachine(machine, planDate);
        while (!hasAvailableTimeForDate(machine.getPlan().get(planDate), workUnit.getDuration(), earliestTime)) {
            planDate = planDate.plusDays(1);
            earliestTime = workdayFrom;
            if (!machine.getPlan().containsKey(planDate)) openNewDateForMachine(machine, planDate);
        }
        LocalTime endTime = addWorkUnitToMachinePlan(machine.getPlan().get(planDate), workUnit, earliestTime);

        return LocalDateTime.of(planDate, endTime);
    }

    private void openNewDateForMachine(Machine machine, LocalDate planDate) {
        machine.getPlan().put(planDate, new ArrayList<>());
    }

    private boolean hasAvailableTimeForDate(List<WorkUnit> planCurrentDate, double duration, LocalTime earliestTime) {
        LocalTime earliestStart = earliestTime == null ? workdayFrom : earliestTime;
        if (planCurrentDate==null) return duration <= DateTimeUtils.doubleHoursBetween(earliestTime, workdayTo);
        LocalTime intervalStart = workdayFrom;
        LocalTime intervalEnd = workdayTo;
        for (WorkUnit workUnit : planCurrentDate) {
            if (earliestStart.isAfter(workUnit.getBeginTime())) {
                intervalStart = DateTimeUtils.plusDoubleHours(workUnit.getBeginTime(), workUnit.getDuration());
            } else {
                if (duration <= DateTimeUtils.doubleHoursBetween(intervalStart.isBefore(earliestStart) ? earliestStart : intervalStart, workUnit.getBeginTime())) {
                    intervalStart = intervalStart.isBefore(earliestStart) ? earliestStart : intervalStart;
                    intervalEnd = workUnit.getBeginTime();
                    break;
                } else {
                    intervalStart = DateTimeUtils.plusDoubleHours(workUnit.getBeginTime(), workUnit.getDuration());
                }
            }
        }
        intervalStart = intervalStart.isBefore(earliestStart) ? earliestStart : intervalStart;

        return duration <= DateTimeUtils.doubleHoursBetween(intervalStart, intervalEnd);
    }

    private LocalTime addWorkUnitToMachinePlan(List<WorkUnit> machinePlanForDate, WorkUnit newWorkUnit, LocalTime earliestTime) {

        LocalTime earliestStart = earliestTime == null ? workdayFrom : earliestTime;
        int index = 0;
        LocalTime intervalStart = workdayFrom;
        LocalTime intervalEnd = workdayTo;
        for (WorkUnit workUnit : machinePlanForDate) {
            index++;
            if (earliestStart.isAfter(workUnit.getBeginTime())) {
                intervalStart = DateTimeUtils.plusDoubleHours(workUnit.getBeginTime(), workUnit.getDuration());
            } else {
                if (newWorkUnit.getDuration() <= DateTimeUtils.doubleHoursBetween(intervalStart.isBefore(earliestStart) ? earliestStart : intervalStart, workUnit.getBeginTime())) {
                    intervalStart = intervalStart.isBefore(earliestStart) ? earliestStart : intervalStart;
                    intervalEnd = workUnit.getBeginTime();
                    break;
                } else {
                    intervalStart = DateTimeUtils.plusDoubleHours(workUnit.getBeginTime(), workUnit.getDuration());
                }
            }
        }
        intervalStart = intervalStart.isBefore(earliestStart) ? earliestStart : intervalStart;

        if (newWorkUnit.getDuration() <= DateTimeUtils.doubleHoursBetween(intervalStart, intervalEnd)) {
            newWorkUnit.setBeginTime(intervalStart);
            machinePlanForDate.add(index, newWorkUnit);
            return DateTimeUtils.plusDoubleHours(newWorkUnit.getBeginTime(), newWorkUnit.getDuration());
        }
        return null;
    }

    public ReportTemplate createReport(Map<StepType, List<Machine>> machines, List<OrderDto> orderDtos) throws JsonProcessingException {
        return ReportService.createReport(machines, orderDtos, LocalDateTime.of(firstDate, workdayFrom));
    }

    // ============================= ====================
    public List<ReportTemplate> scheduleIntuitive(List<OrderDto> orderDtos) throws JsonProcessingException {

        Map<StepType, List<Machine>> machines = DataStructBuilders.buildMachinesFromStations(stationsDtos, this.firstDate);
        Map<String, Stack<WorkUnit>> orders = createOrders(orderDtos);
                DataStructBuilders.buildWorkUnitsFromOrders(orderDtos, orderTypes);

        LocalDate currentDate = firstDate;
        List<LocalDateTime> currentFinish = Stream.generate(() -> LocalDateTime.MIN)
                .limit(orders.size())
                .collect(Collectors.toList());

        while (!allEmpty(orders)) {
            boolean continueWithCurrentDate = true;
            while (continueWithCurrentDate) {
                continueWithCurrentDate = false;
                int counter = -1;
                for (Stack<WorkUnit> order : orders.values()) {
                    counter++;
                    if (order.isEmpty()) continue;
                    WorkUnit workUnit = order.pop();
                    LocalDateTime newDateTime = addWorkUnitToMachineGroup(
                            machines.get(workUnit.getStepType()),
                            currentDate,
                            workUnit,
                            currentFinish.get(counter).equals(LocalDateTime.MIN) ? null : currentFinish.get(counter)
                    );
                    if (newDateTime != null) {
                        currentFinish.set(counter, newDateTime);
                        continueWithCurrentDate = true;
                    } else order.push(workUnit);
                }
            }
            currentDate = currentDate.plusDays(1);
        }

        return Arrays.asList(ReportService.createReport(machines, orderDtos, LocalDateTime.of(firstDate, workdayFrom)));
    }

    private Map<String, Stack<WorkUnit>> createOrders(List<OrderDto> orderDtos) {
        Map<String, Stack<WorkUnit>> ordersByWorkUnits = new HashMap<>();
        for (OrderDto orderDto : orderDtos) {
            Stack<WorkUnit> stack = new Stack<>();
            int orderTypeId = DataStructBuilders.typeIdByName(orderTypes, orderDto.getType());
            for (int j = orderTypes.get(orderTypeId).steps.size() - 1; j >= 0; j--) {
                WorkUnit newWorkUnit = WorkUnit.builder()
                        .orderId(orderDto.getOrderId())
                        .stepType(orderTypes.get(orderTypeId).steps.get(j).getStepType())
                        .beginTime(null)
                        .duration(orderTypes.get(orderTypeId).steps.get(j).getDuration())
                        .build();
                stack.push(newWorkUnit);
            }
            Stack<WorkUnit> newStack = new Stack<>();
            newStack.addAll(stack);
            ordersByWorkUnits.put(orderDto.getOrderId(), newStack);
        }
        return ordersByWorkUnits;
    }

    private LocalDateTime addWorkUnitToMachineGroup(
            List<Machine> machineGroup, LocalDate currentDate, WorkUnit workUnit, LocalDateTime earliestDateTime) {

        if (earliestDateTime!=null && earliestDateTime.toLocalDate().isAfter(currentDate)) return null;

        LocalTime earliestTime = earliestDateTime == null || earliestDateTime.toLocalDate().isBefore(currentDate) ? workdayFrom :
                earliestDateTime.toLocalTime();

        Machine bestMachine = null;
        LocalTime bestTime = null;
        for (Machine machine : machineGroup) {
            if (hasAvailableTimeForDate(machine.getPlan().get(currentDate), workUnit.getDuration(), earliestTime)) {
                if (bestMachine==null) {
                    bestMachine = machine;
                    bestTime = availableTimeForDate(machine.getPlan().get(currentDate), workUnit.getDuration(), earliestTime);
                } else {
                    LocalTime newTime = availableTimeForDate(machine.getPlan().get(currentDate), workUnit.getDuration(), earliestTime);
                    if (bestTime.isAfter(newTime)) {
                        bestMachine = machine;
                        bestTime = newTime;
                    }
                }
            }
        }

        return bestMachine==null?null : addWorkUnitToMachine(bestMachine, workUnit, LocalDateTime.of(currentDate, earliestTime));
    }

    private LocalTime availableTimeForDate(List<WorkUnit> planCurrentDate, double duration, LocalTime earliestTime) {
        LocalTime earliestStart = earliestTime == null ? workdayFrom : earliestTime;
        if (planCurrentDate==null)
            return duration <= DateTimeUtils.doubleHoursBetween(earliestStart, workdayTo) ? earliestStart : null;
        LocalTime intervalStart = workdayFrom;
        LocalTime intervalEnd = workdayTo;
        for (WorkUnit workUnit : planCurrentDate) {
            if (earliestStart.isAfter(workUnit.getBeginTime())) {
                intervalStart = DateTimeUtils.plusDoubleHours(workUnit.getBeginTime(), workUnit.getDuration());
            } else {
                if (duration <= DateTimeUtils.doubleHoursBetween(intervalStart.isBefore(earliestStart) ? earliestStart : intervalStart, workUnit.getBeginTime())) {
                    intervalStart = intervalStart.isBefore(earliestStart) ? earliestStart : intervalStart;
                    intervalEnd = workUnit.getBeginTime();
                    break;
                } else {
                    intervalStart = DateTimeUtils.plusDoubleHours(workUnit.getBeginTime(), workUnit.getDuration());
                }
            }
        }
        intervalStart = intervalStart.isBefore(earliestStart) ? earliestStart : intervalStart;

        return duration <= DateTimeUtils.doubleHoursBetween(intervalStart, intervalEnd) ? intervalStart : null;
    }

    private boolean allEmpty(Map<?, Stack<WorkUnit>> orders) {
        for (Stack<WorkUnit> stack : orders.values()) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

}