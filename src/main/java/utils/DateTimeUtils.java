package utils;

import java.time.LocalTime;

import static java.time.temporal.ChronoUnit.MINUTES;

public class DateTimeUtils {

    public static LocalTime plusDoubleHours(LocalTime initial, double duration) {
        return initial.plusHours(((int) duration)).plusMinutes((int) (60 * (duration % 1)));
    }

    public static double doubleHoursBetween(LocalTime from, LocalTime to) {
        return (double) MINUTES.between(from, to) / 60;
    }

}
