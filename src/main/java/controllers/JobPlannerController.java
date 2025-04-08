package controllers;

import builders.FileExtractors;
import com.opencsv.exceptions.CsvException;
import model.OrderType;
import model.dto.OrderDto;
import model.dto.StationDto;
import org.json.JSONObject;
import services.OrderSchedulerService;

import java.io.IOException;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public class JobPlannerController {

    private static final String FILES_PATH = "data\\initial\\";
    private static final String STATIONS_JSON = "stations.json";
    private static final String ORDER_TYPES_JSON = "work_order_types.json";
    private static final String ORDERS_CSV = "work orders.csv";
    private static final LocalTime WORKDAY_FROM = LocalTime.parse("07:00");
    private static final LocalTime WORKDAY_TO = LocalTime.parse("16:00");

    public static void main(String[] args) throws IOException, CsvException {

        Map<Integer, OrderType> orderTypes = FileExtractors.buildOrderTypes(FILES_PATH+ORDER_TYPES_JSON);
        List<StationDto> stations = FileExtractors.buildStations(FILES_PATH+STATIONS_JSON);
        List<OrderDto> orders = FileExtractors.readOrdersCsv(FILES_PATH+ORDERS_CSV);

        OrderSchedulerService orderSchedulerService = new OrderSchedulerService(stations, orderTypes, null, WORKDAY_FROM, WORKDAY_TO);

        List<JSONObject> solutions = orderSchedulerService.scheduleOrders(orders);

        if (solutions!=null && !solutions.isEmpty() && solutions.get(0)!=null) {
            System.out.println(solutions.get(0).toString(4) + "\nBest solutions: " + solutions.size());
        }
    }

}
