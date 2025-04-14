package services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import model.StepType;
import model.Machine;
import model.WorkUnit;
import model.dto.OrderDto;
import model.report.*;
import utils.DateTimeUtils;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.HOURS;

public class ReportService {

    public static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String OUTPUT_PATH = "data\\output\\";

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
            if (me.getValue().size()>0) {
                LocalDateTime endDateTime = LocalDateTime.of(
                        me.getKey(),
                        DateTimeUtils.plusDoubleHours(
                                me.getValue().get(me.getValue().size() - 1).getBeginTime(),
                                me.getValue().get(me.getValue().size() - 1).getDuration()));
                retval[0] = retval[0].isAfter(endDateTime) ? retval[0] : endDateTime;
            }
        });
        return retval[0];
    }

    public static ReportTemplate createReport(Map<StepType, List<Machine>> machines, List<OrderDto> orderDtos, LocalDateTime firstDate) throws JsonProcessingException {
        Map<String, List<ReportService.ReportNode>> ordersReport = buildOrdersMap(machines);
        ArrayList<Map.Entry<String, List<ReportService.ReportNode>>> ordersReportAsList = new ArrayList<>(ordersReport.entrySet());
        ordersReportAsList.sort(Map.Entry.comparingByKey());
        List<OrderTemplate> orders = new ArrayList<>();
        ordersReportAsList.forEach((entry) -> {

            entry.getValue().sort(Comparator.comparing(ReportService.ReportNode::getBeginDateTime));
            List<OperationTemplate> steps = new ArrayList<>();
            entry.getValue().forEach(reportNode -> {
                steps.add(OperationTemplate.builder()
                        .operation(reportNode.getOperation())
                        .place(reportNode.getPlace())
                        .date(reportNode.getBeginDateTime().toLocalDate().toString())
                        .time(reportNode.getBeginDateTime().toLocalTime() + "-" + reportNode.getBeginDateTime().toLocalTime().plusHours(((int) reportNode.getDuration())).plusMinutes((int) (60.0 * (reportNode.getDuration() % 1))))
                        .build());
            });
            orders.add(OrderTemplate.builder()
                    .orderId(entry.getKey())
                    .operations(steps)
                    .build());
        });
        LocalDateTime end = calcEndTime(machines);
        ReportTemplate retval = ReportTemplate.builder()
                .execution(OrderBatchDataTemplate.builder()
                        .orders(orders.size())
                        .orderTypes((int) orderDtos.stream().map(OrderDto::getType).distinct().count())
                        .makespan(HOURS.between(firstDate, end) + " hours (end " + end.toString() + ")")
                        .build())
                .orders(orders)
                .stations(buildReportByStations(machines))
                .build();

        String fileName = new File(OUTPUT_PATH).list().length + ".json";
        try {
            objectMapper.writeValue(new File(OUTPUT_PATH + fileName), retval);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return retval;
    }

    public static Map<StepType, List<StationTemplate>> buildReportByStations(Map<StepType, List<Machine>> machines) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        Map<StepType, List<StationTemplate>> retval = new TreeMap<>();
        machines.forEach((operationType, machineGroup) -> {
            List<StationTemplate> stations = new ArrayList<>();
            machineGroup.forEach(machine -> {
                Map<String, List<String>> orders = new TreeMap<>();

                LocalDate emptyIntervalStart = null;
                for (Map.Entry<LocalDate, List<WorkUnit>> entry : machine.getPlan().entrySet()) {

                    if (entry.getValue().isEmpty()) {
                        if (emptyIntervalStart == null) emptyIntervalStart = entry.getKey();
                        else continue;
                    } else {
                        List<String> jobs = new ArrayList<>();
                        if (emptyIntervalStart != null) emptyIntervalStart = null;

                        entry.getValue().forEach(workUnit -> {
                            jobs.add(workUnit.getBeginTime() + "-" + DateTimeUtils.plusDoubleHours(workUnit.getBeginTime(), workUnit.getDuration()) + " " + workUnit.getOrderId());
                        });
                        jobs.sort(Comparator.naturalOrder());
                        orders.put(entry.getKey().toString(), jobs);
                    }
                }
                stations.add(StationTemplate.builder()
                        .name(machine.getStationName())
                        .index(machine.getIndex())
                        .orders(orders)
                        .build());
            });
            retval.put(operationType, stations);
        });
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
