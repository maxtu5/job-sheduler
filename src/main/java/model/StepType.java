package model;

public enum StepType {
    TOOLPREP("Tool Preparation"),
    CUTTING("Cutting"),
    LAYUP("Layup")
    ,
    CURE("Cure"),
    COOLING("Cooling"),
    FINISH("Surface finish"),
    ;

    private String label;

    StepType(String label) {
        this.label = label;
    }
    
    public static StepType fromLabel(String label) {
        for (StepType ot: StepType.values()) {
            if (ot.label.equalsIgnoreCase(label)) return ot;
        }
        return null;
    }

}
