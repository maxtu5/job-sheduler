package builders;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import model.OrderType;
import model.Step;
import model.StepType;
import model.dto.OrderDto;
import model.dto.StationDto;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FileExtractors {

    public static Map<Integer, OrderType> buildOrderTypes(String fileName) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(fileName));
        JSONArray orderTypes = new JSONObject(reader.lines().collect(Collectors.joining()))
                .getJSONArray("dataList");
        Map<Integer, OrderType> retval = new HashMap<>();
        for (int i = 0; i < orderTypes.length(); i++) {
            JSONObject orderType = orderTypes.getJSONObject(i);
            String typeName = orderType.getString("name");
            JSONArray typeOperations = orderType.getJSONArray("operations");
            List<Step> typeOperationsList = new ArrayList<>();
            for (int j = 0; j < typeOperations.length(); j++) {
                JSONObject operation = typeOperations.getJSONObject(j);
                StepType operationName = StepType.fromLabel(operation.getString("operation"));
                double operationDuration = operation.getDouble("durationHours");
                typeOperationsList.add(new Step(operationName, operationDuration));
            }
            retval.put(i, new OrderType(typeName, typeOperationsList));
        }
        return retval;
    }

    public static List<StationDto> buildStations(String fileName) throws FileNotFoundException {
        BufferedReader reader = new BufferedReader(new FileReader(fileName));
        JSONArray stations = new JSONObject(reader.lines().collect(Collectors.joining()))
                .getJSONArray("dataList");
        List<StationDto> retval = new ArrayList<>();
        for (int i = 0; i < stations.length(); i++) {
            JSONObject station = stations.getJSONObject(i);
            String stationName = station.getString("name");
            StepType operationName = StepType.fromLabel(station.getString("operation"));
            int stationCapacity = station.getInt("capacity");
            StationDto st = new StationDto(operationName, stationName, stationCapacity);
            retval.add(st);
        }
        return retval;
    }

    public static List<OrderDto> readOrdersCsv(String fileName) throws IOException, CsvException {
        List<OrderDto> retval = new ArrayList<>();
        try (Reader reader = new BufferedReader(new FileReader(fileName))) {
            try (CSVReader csvReader = new CSVReader(reader)) {
                csvReader.readNext();
                List<String[]> strings = csvReader.readAll();
                for (String[] str: strings) {
                    DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
                    builder.append(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    DateTimeFormatter dtf = builder.toFormatter();
                    retval.add(new OrderDto(str[0], str[1], LocalDate.parse(str[2], dtf)));
                }
            }
        }
        return retval;
    }

}
