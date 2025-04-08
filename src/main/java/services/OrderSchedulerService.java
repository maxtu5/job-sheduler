package services;

import builders.DataStructBuilders;
import model.*;
import model.dto.OrderDto;
import model.dto.StationDto;
import org.json.JSONObject;
import utils.DateTimeUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.HOURS;

public class OrderSchedulerService {

    private static final int MAX_ORDERS_IN_PORTION = 9;
    private static LocalTime workdayFrom;
    private static LocalTime workdayTo;

    private final Map<Integer, OrderType> orderTypes;
    private final List<StationDto> stationsDtos;
    private final LocalDate firstDate;

    LocalDateTime earliestEndTime = null;

    private final PermutationsService permutationsService;

    public OrderSchedulerService(List<StationDto> stations, Map<Integer, OrderType> orderTypes, LocalDate firstDate, LocalTime from, LocalTime to) {
        this.orderTypes = orderTypes;
        stationsDtos = stations;
        this.firstDate = firstDate == null ? LocalDate.now().plusDays(1) : firstDate;
        workdayFrom = from;
        workdayTo = to;
        permutationsService = new PermutationsService(orderTypes, HOURS.between(workdayFrom, workdayTo));
    }

    public List<JSONObject> scheduleOrders(List<OrderDto> orderDtos) {

        int ordersDone = 0;
        List<List<OrderDto>> ordersByPortions = new ArrayList<>();
        List<List<List<ScheduleNode>>> scheduleListsByPortions = new ArrayList<>();

        for (int i = 0; i < orderDtos.size() / MAX_ORDERS_IN_PORTION; i++) {
            List<OrderDto> subList = orderDtos.subList(ordersDone, ordersDone + MAX_ORDERS_IN_PORTION);
            ordersByPortions.add(subList);
            scheduleListsByPortions.add(scheduleBatchOfOrders(subList));
            ordersDone += MAX_ORDERS_IN_PORTION;
        }
        List<OrderDto> subList = orderDtos.subList(ordersDone, orderDtos.size());
        ordersByPortions.add(subList);
        scheduleListsByPortions.add(scheduleBatchOfOrders(subList));

        Map<StepType, List<Machine>> machines = DataStructBuilders.buildMachinesFromStations(stationsDtos, this.firstDate);

        for (int i = 0; i < scheduleListsByPortions.size(); i++) {
            proceedSchedule(scheduleListsByPortions.get(i).get(0), ordersByPortions.get(i), machines);
        }

//        reportByOrders(machines);
//        reportByStations(machines);
        return Arrays.asList(createReport(machines));
    }

    public List<List<ScheduleNode>> scheduleBatchOfOrders(List<OrderDto> orderDtos) {

        Map<Integer, Integer> orderNumsByTypeIds = orderDtos.stream()
                .map(OrderDto::getType).collect(Collectors.groupingBy(t -> t, Collectors.counting()))
                .entrySet().stream()
                .collect(Collectors.toMap(me -> DataStructBuilders.typeIdByName(orderTypes, me.getKey()),
                        me -> me.getValue().intValue()));

        Map<StepType, List<Machine>> machines = DataStructBuilders.buildMachinesFromStations(stationsDtos, this.firstDate);

        List<List<ScheduleNode>> allSchedules = permutationsService.generateAllSchedules(
                machines.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())),
                orderNumsByTypeIds);
        allSchedules = refineSchedules(allSchedules, orderDtos);
        earliestEndTime = proceedSchedule(allSchedules.get(0), orderDtos, machines);
//        System.out.println("Solutions " + allSchedules.size());
//        System.out.println("Earliest completion time " + earliestEndTime);
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
        while (!hasTime(machine.getPlan().get(planDate), workUnit.getDuration(), earliestTime)) {
            planDate = planDate.plusDays(1);
            earliestTime = workdayFrom;
            if (!machine.getPlan().containsKey(planDate)) openNewDateForMachine(machine, planDate);
        }
        LocalTime endTime = addWorkUnitToPlan(machine.getPlan().get(planDate), workUnit, earliestTime);

        return LocalDateTime.of(planDate, endTime);
    }

    private void openNewDateForMachine(Machine machine, LocalDate planDate) {
        machine.getPlan().put(planDate, new ArrayList<>());
    }

    // ============================= ====================
//    public void scheduleIntuitive(List<OrderDto> orderDtos) {
//
//        Map<StepType, List<Machine>> machines = DataStructBuilders.buildMachinesFromStations(stationsDtos, this.firstDate);
//        sortByPriority(orderDtos);
//        Map<Integer, Stack<WorkUnit>> orders = DataStructBuilders.buildWorkUnitsFromOrders(orderDtos, orderTypes);
//
//        LocalDate currentDate = firstDate;
//        List<LocalDateTime> currentFinish = Stream.generate(() -> LocalDateTime.MIN)
//                .limit(orders.size())
//                .collect(Collectors.toList());
//
//
////        openNewDateForResources(currentDate.plusDays(1), 30);
//
//        while (!allEmpty(orders)) {
//            boolean continueWithCurrentDate = true;
//            while (continueWithCurrentDate) {
//                continueWithCurrentDate = false;
//                int counter = -1;
//                for (Stack<WorkUnit> order : orders.values()) {
//                    counter++;
//                    if (order.isEmpty()) continue;
//                    WorkUnit workUnit = order.pop();
//                    LocalDateTime newDateTime = addWorkUnitToMachineGroup(
//                            machines.get(workUnit.getStepType()),
//                            currentDate,
//                            workUnit,
//                            currentFinish.get(counter).equals(LocalDateTime.MIN) ? null : currentFinish.get(counter)
//                    );
//                    if (newDateTime != null) {
//                        currentFinish.set(counter, newDateTime);
//                        continueWithCurrentDate = true;
//                    } else order.push(workUnit);
//                }
//            }
//            currentDate = currentDate.plusDays(1);
//            openNewDateForResources(currentDate, 1);
//        }
//    }
//
//    private void sortByPriority(List<OrderDto> orders) {
//        orders.sort(Comparator.comparing(OrderDto::getDueDate));
//        LocalDate earliestDate = orders.get(0).getDueDate();
//        System.out.println(earliestDate);
//        orders.sort((o1, o2) -> (int) (durationWithDueDate(o1, earliestDate) - durationWithDueDate(o2, earliestDate)));
//    }
//
//    private void openNewDateForResources(LocalDate currentDate, int dates) {
//        LocalDate dateToAdd = currentDate;
//        for (int i = 0; i < dates; i++) {
//            for (Map.Entry<StepType, List<Machine>> entry : machines.entrySet()) {
//                for (Machine machine : entry.getValue()) {
//                    machine.getPlan().put(dateToAdd, new ArrayList<>());
//                }
//            }
//            dateToAdd = dateToAdd.plusDays(1);
//        }
//    }
//
//    private LocalDateTime addWorkUnitToMachineGroup(List<Machine> machineGroup, LocalDate currentDate,
//                                                    WorkUnit workUnit, LocalDateTime earliestDateTime) {
//
//        LocalDate[] planningDate = new LocalDate[]{currentDate};
//        for (int i = 0; i < 1; i++) {
//
//
//            LocalTime earliestTime = earliestDateTime == null ? null :
//                    earliestDateTime.toLocalDate().equals(planningDate[0]) ? earliestDateTime.toLocalTime() :
//                            (earliestDateTime.toLocalDate().isBefore(planningDate[0]) ? workdayFrom : workdayTo);
//
//
//            LocalTime earliestAvailableTime = machineGroup.stream()
//                    .map(machine -> availabilityTime(machine.getPlan().get(planningDate[0])))
//                    .min(Comparator.naturalOrder())
//                    .orElse(workdayFrom);
//
//            for (Machine machine : machineGroup) {
//                List<WorkUnit> planCurrentDate = machine.getPlan().get(planningDate[0]);
//                if (availabilityTime(planCurrentDate).equals(earliestAvailableTime) &&
//                        hasTime(planCurrentDate, workUnit.getDuration(), earliestTime)) {
//                    LocalTime endTime = addWorkUnitToPlan(planCurrentDate, workUnit, earliestTime);
//                    return endTime == null ? null : LocalDateTime.of(planningDate[0], endTime);
//                }
//            }
//            planningDate[0] = planningDate[0].plusDays(1);
//        }
//
//        return null;
//    }
//
//    private LocalTime availabilityTime(List<WorkUnit> workUnits) {
//        return workUnits.size() == 0 ? workdayFrom : DateTimeUtils.plusDoubleHours(
//                workUnits.get(workUnits.size() - 1).getBeginTime(),
//                workUnits.get(workUnits.size() - 1).getDuration()
//        );
//    }
//
    private LocalTime addWorkUnitToPlan(List<WorkUnit> planCurrentDate, WorkUnit newWorkUnit, LocalTime
            earliestTime) {
        LocalTime earliestStart = earliestTime == null ? workdayFrom : earliestTime;
        int current = -1;
        LocalTime intervalStart = workdayFrom;
        LocalTime intervalEnd = workdayTo;
        for (WorkUnit workUnit : planCurrentDate) {
            current++;
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
            newWorkUnit.setBeginTime(earliestStart);
            current = Math.max(current, 0);
            planCurrentDate.add(current, newWorkUnit);
            return DateTimeUtils.plusDoubleHours(newWorkUnit.getBeginTime(), newWorkUnit.getDuration());
        }
        return null;
    }

    private boolean hasTime(List<WorkUnit> planCurrentDate, double duration, LocalTime earliestTime) {
        LocalTime start = earliestTime == null ? workdayFrom : earliestTime;

        LocalTime endTime = planCurrentDate.isEmpty() ? workdayFrom :
                DateTimeUtils.plusDoubleHours(planCurrentDate.get(planCurrentDate.size() - 1).getBeginTime(),
                        planCurrentDate.get(planCurrentDate.size() - 1).getDuration());

        LocalTime realStart = start.isAfter(endTime) ? start : endTime;

        return duration <= DateTimeUtils.doubleHoursBetween(realStart, workdayTo);
    }
//
//    private boolean allEmpty(Map<Integer, Stack<WorkUnit>> orders) {
//        for (Stack<WorkUnit> stack : orders.values()) {
//            if (!stack.isEmpty()) return false;
//        }
//        return true;
//    }
//
//    private double durationWithDueDate(OrderDto order, LocalDate earliestDate) {
//        return DAYS.between(earliestDate, order.getDueDate()) * HOURS.between(workdayFrom, workdayTo) -
//                orderTypes.get(DataStructBuilders.typeIdByName(orderTypes, order.getType())).steps.stream()
//                        .mapToDouble(operation -> operation.getDuration() / machines.get(operation.getStepType()).size())
//                        .sum();
//    }

    public void reportByStations(Map<StepType, List<Machine>> machines) {
        ReportService.reportByStations(machines);
    }

    public void reportByOrders(Map<StepType, List<Machine>> machines) {
        ReportService.reportByOrders(machines);
    }

    public JSONObject createReport(Map<StepType, List<Machine>> machines) {
        return ReportService.createReport(machines);
    }

    //
//    public void reportMetrics() {
//        ReportService.reportMetrics(machines, firstDate);
//    }
//
//    public void reportEndTime() {
//        removeEmptyDatesForMachines(machines);
//        System.out.println(ReportService.calcEndTime(machines));
//    }
}