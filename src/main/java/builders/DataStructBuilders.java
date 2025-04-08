package builders;

import model.*;
import model.dto.OrderDto;
import model.dto.StationDto;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class DataStructBuilders {

    public static Map<StepType, List<Machine>> buildMachinesFromStations(List<StationDto> stations, LocalDate firstDate) {
        Map<StepType, List<Machine>> resources = new HashMap<>();
        for (StationDto station : stations) {
            for (int i = 0; i < station.getCapacity(); i++) {
                List<Machine> initial = new ArrayList<>();
                initial.add(new Machine(station.getName(), i, firstDate));
                resources.merge(station.getOperation(), initial, (l1, l2) -> {
                    l1.addAll(l2);
                    return l1;
                });
            }
        }
        return resources;
    }

    public static Map<Integer, Stack<WorkUnit>> buildWorkUnitsFromOrders(List<OrderDto> orderDtos, Map<Integer, OrderType> orderTypes) {
        Map<Integer, Stack<WorkUnit>> ordersByWorkUnits = new HashMap<>();
        for (OrderDto orderDto : orderDtos) {
            Stack<WorkUnit> stack = new Stack<>();
            int orderTypeId = typeIdByName(orderTypes, orderDto.getType());
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
            ordersByWorkUnits.put(orderTypeId, newStack);
        }
        return ordersByWorkUnits;
    }

    public static Map<Integer, Map<String, Stack<WorkUnit>>> buildWorkUnitsMapFromOrders(
            List<OrderDto> orderDtos, Map<Integer, OrderType> orderTypes) {

        Map<Integer, Stack<WorkUnit>> orders = buildWorkUnitsFromOrders(orderDtos, orderTypes);
        return orderTypes.keySet().stream()
                .collect(Collectors.toMap(
                        typeId -> typeId,
                        typeId -> orderDtos.stream()
                                .filter(t -> t.getType().equals(orderTypes.get(typeId).name))
                                .collect(Collectors.toMap(
                                        OrderDto::getOrderId,
                                        o -> copyStackUpdateId(orders.get(typeIdByName(orderTypes, o.getType())), o.getOrderId())))));
    }

    private static Stack<WorkUnit> copyStackUpdateId(Stack<WorkUnit> orders, String orderId) {
        Stack<WorkUnit> transit = new Stack<>();
        Stack<WorkUnit> retval = new Stack<>();

        while (!orders.isEmpty()) transit.push(orders.pop());
        while (!transit.isEmpty()) {
            WorkUnit workUnit = transit.pop();
            WorkUnit newWorkUnit = WorkUnit.builder()
                    .orderId(orderId)
                    .stepType(workUnit.getStepType())
                    .duration(workUnit.getDuration())
                    .beginTime(workUnit.getBeginTime())
                    .build();
            orders.push(workUnit);
            retval.push(newWorkUnit);
        }
        return retval;
    }

    public static Integer typeIdByName(Map<Integer, OrderType> orderTypes, String typeName) {
        return orderTypes.entrySet().stream()
                .filter(ot -> ot.getValue().name.equals(typeName))
                .findAny().orElse(null)
                .getKey();
    }

}
