package services;

import lombok.AllArgsConstructor;
import lombok.Getter;
import model.StepType;
import model.Machine;
import model.WorkUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.DateTimeUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;

public class ReportService {

    private static Map<String, List<ReportNode>> buildOrdersMap(Map<StepType, List<Machine>> resources) {
        return resources.values().stream()
                .flatMap(Collection::stream)
                .flatMap(machine -> {
                    String place = machine.getStationName() + " " + machine.getIndex();
                    return machine.getPlan().entrySet().stream()
                            .flatMap(e -> {
                                LocalDate date = e.getKey();
                                return e.getValue().stream()
                                        .map(workUnit -> {
                                            LocalDateTime dateTime = LocalDateTime.of(date, workUnit.getBeginTime());
                                            return new ReportService.ReportNode(workUnit.getOrderId(), workUnit.getStepType(), place, dateTime, workUnit.getDuration());
                                        });
                            });
                }).collect(Collectors.groupingBy(ReportService.ReportNode::getOrderId, Collectors.mapping((a) -> a, Collectors.toList())));
    }

    public static LocalDateTime calcEndTime(Map<StepType, List<Machine>> machines) {
        List<Map.Entry<LocalDate, List<WorkUnit>>> collect = machines.values().stream()
                .flatMap(Collection::stream)
                .map(Machine::getPlan)
                .map(TreeMap::lastEntry)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        LocalDateTime[] retval = new LocalDateTime[]{LocalDateTime.of(collect.get(0).getKey(),
                DateTimeUtils.plusDoubleHours(collect.get(0).getValue().get(collect.get(0).getValue().size() - 1).getBeginTime(),
                        collect.get(0).getValue().get(collect.get(0).getValue().size() - 1).getDuration()))};
        collect.forEach(me -> {
            LocalDateTime endDateTime = LocalDateTime.of(
                    me.getKey(),
                    DateTimeUtils.plusDoubleHours(
                            me.getValue().get(me.getValue().size() - 1).getBeginTime(),
                            me.getValue().get(me.getValue().size() - 1).getDuration()));
            retval[0] = retval[0].isAfter(endDateTime) ? retval[0] : endDateTime;
        });
        return retval[0];
    }

    public static JSONObject createReport(Map<StepType, List<Machine>> machines) {
        Map<String, List<ReportService.ReportNode>> ordersReport = buildOrdersMap(machines);
        ArrayList<Map.Entry<String, List<ReportService.ReportNode>>> ordersReportAsList = new ArrayList<>(ordersReport.entrySet());
        ordersReportAsList.sort(Map.Entry.comparingByKey());
        JSONArray orders = new JSONArray();
        ordersReportAsList.forEach((entry) -> {
            JSONObject order = new JSONObject();
            entry.getValue().sort(Comparator.comparing(ReportService.ReportNode::getBeginDateTime));
            JSONArray steps = new JSONArray();
            entry.getValue().forEach(reportNode -> {
                JSONObject step = new JSONObject();
                step.put("operation", reportNode.getOperation());
                step.put("place", reportNode.getPlace());
                step.put("date", reportNode.getBeginDateTime().toLocalDate());
                step.put("time", reportNode.getBeginDateTime().toLocalTime() + "-" + reportNode.getBeginDateTime().toLocalTime().plusHours(((int) reportNode.getDuration())).plusMinutes((int) (60.0 * (reportNode.getDuration() % 1))));
                steps.put(step);
            });
            order.put("orderId", entry.getKey());
            order.put("operations", steps);
            orders.put(order);
        });
        JSONObject retval = new JSONObject();
        retval.put("orders", orders);
        return retval;
    }

    @AllArgsConstructor
    @Getter
    private static class ReportNode {

        private String orderId;
        private StepType operation;
        private String place;
        private LocalDateTime beginDateTime;
        private double duration;
    }
}
