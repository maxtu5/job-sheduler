package model;

import lombok.Getter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

@Getter
public class Machine {

    public Machine(String stationName, int index, LocalDate firstDate) {
        this.stationName = stationName;
        this.index = index;
        plan = new TreeMap<>();
        plan.put(firstDate, new ArrayList<>());
    }

    private String stationName;
    private int index;
    private TreeMap<LocalDate, List<WorkUnit>> plan;

}
