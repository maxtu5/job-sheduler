package controllers;

import builders.FileExtractors;
import com.opencsv.exceptions.CsvException;
import model.OrderType;
import model.dto.OrderDto;
import model.dto.StationDto;
import model.report.ReportTemplate;
import services.OrderSchedulerService;
import services.ReportService;

import java.io.IOException;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public class JobPlannerController {

    private static final String FILES_PATH = "data\\initial\\";
    private static final String STATIONS_JSON = "stations.json";
    private static final String ORDER_TYPES_JSON = "work_order_types.json";
    private static final String ORDERS_CSV = "work orders.csv";
    private static final int MAX_ORDERS_IN_PORTION = 1000;
    private static final LocalTime WORKDAY_FROM = LocalTime.parse("07:00");
    private static final LocalTime WORKDAY_TO = LocalTime.parse("16:00");

    public static void main(String[] args) throws IOException, CsvException {

        Map<Integer, OrderType> orderTypes = FileExtractors.buildOrderTypes(FILES_PATH+ORDER_TYPES_JSON);
        List<StationDto> stations = FileExtractors.buildStations(FILES_PATH+STATIONS_JSON);
        List<OrderDto> orders = FileExtractors.readOrdersCsv(FILES_PATH+ORDERS_CSV);

        OrderSchedulerService orderSchedulerService = new OrderSchedulerService(stations, orderTypes, null, WORKDAY_FROM, WORKDAY_TO, MAX_ORDERS_IN_PORTION);

//        List<ReportTemplate> solutions = orderSchedulerService.scheduleOrdersA(orders, 16, 12, 16);
//        List<ReportTemplate> solutions = orderSchedulerService.scheduleOrders(orders);
        List<ReportTemplate> solutions = orderSchedulerService.scheduleIntuitive(orders);


        if (solutions!=null && !solutions.isEmpty() && solutions.get(0)!=null) {
            System.out.println(ReportService.objectMapper.writeValueAsString(solutions.get(0)) + "\nBest solutions: " + solutions.size());
        }
    }

}
