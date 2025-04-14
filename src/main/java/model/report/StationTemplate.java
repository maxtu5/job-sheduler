package model.report;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public class StationTemplate {

    public String name;
    public int index;
    public Map<String, List<String>> orders;

}
