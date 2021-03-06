package tech.tongyu.bct.service.quantlib.financial.dateservice;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.TreeSet;

import static tech.tongyu.bct.service.quantlib.financial.dateservice.Utils.getUnAdjEndDate;
import static tech.tongyu.bct.service.quantlib.financial.dateservice.Utils.isDayEom;

/**
 * A schedule is simply a collection of dates. Various methods are provided
 * to generated a schedule according to roll and business day adjustment rules.
 */
public class Schedule {
    /**
     * Generate a schedule from a start date and an end date.
     * CAUTION: End date usually should be unadjusted. Otherwise rolling can easily
     * cause stub periods either at the front or back.
     * @param start Schedule start date
     * @param end Schedule end date
     * @param freq Rolling frequency
     * @param roll Rolling convention
     * @param adj Business day adjustment convention
     * @param calendars Business holidays
     * @return Generated schedule as a list of dates
     */
    public static ArrayList<LocalDate> generate(LocalDate start, LocalDate end,
                                                Period freq, Roll roll, BusDayAdj adj,
                                                String[] calendars) {
        boolean adjToEom = (roll == Roll.BACKWARD_EOM || roll == Roll.FORWARD_EOM) && isDayEom(start);
        //   unit
        TemporalUnit unit;
        if (freq.get(ChronoUnit.YEARS) > 0)
            unit = ChronoUnit.YEARS;
        else if (freq.get(ChronoUnit.MONTHS) > 0)
            unit = ChronoUnit.MONTHS;
        else
            unit = ChronoUnit.DAYS;
        long q = freq.get(unit);
        // temporary schedule
        TreeSet<LocalDate> dates = new TreeSet<>();
        // roll forward or backward
        if (roll == Roll.BACKWARD || roll == Roll.BACKWARD_EOM) {
            LocalDate t = end;
            int c = 1;
            while (t.isAfter(start)) {
                dates.add(t);
                t = end.minus(c * q, unit);
                if (adjToEom)
                    t = t.with(TemporalAdjusters.lastDayOfMonth());
                ++c;
            }
            dates.add(start);
        } else {
            LocalDate t = start;
            int c = 1;
            while (t.isBefore(end)) {
                dates.add(t);
                t = start.plus(c * q, unit);
                if (adjToEom)
                    t = t.with(TemporalAdjusters.lastDayOfMonth());
                ++c;
            }
            dates.add(end);
        }
        // adjust by business days
        TreeSet<LocalDate> busDays = new TreeSet<>();
        for (LocalDate t : dates) {
            LocalDate b = adj.adjust(t, calendars);
            busDays.add(b);
        }
        // create the schedule
        return new ArrayList<>(busDays);
    }
    /**
     * Generate a schedule given a start date and a tenor. There are two cases to consider:
     * <p> The start date is EOM and the roll is also EOM. The end date is calculated by
     * adding the tenor to the start date and then adjust to its month end date. Then we roll
     * from the start date (FORWARD_EOM) or from the end date (BACKWARD_EOM) with the given
     * frequency to generate the rest of the unadjusted schedule dates. These dates are all
     * adjusted to month end.
     * <p> The start date is not month end or the roll is not EOM.
     * Then the unadjusted end date is simply start date plus the tenor. The rest of the schedule
     * is generated by rolling from the front (FORWARD or FORWARD_EOM) or back (BACKWARD or BACKWARD_EOM).
     * These dates are not adjusted by EOM.
     *
     * Finally the schedule is adjusted by business day adjustment rules to make sure all of them
     * are good business days.
     * @param start The start date of the schedule
     * @param tenor The tenor of the schedule. For example 6M, 1Y etc.
     * @param freq Roll frequency. For example 1M, 3M, 6M, 1Y etc.
     * @param roll Roll convention. See {@link Roll}
     * @param adj Business day adjustment convention. See {@link BusDayAdj}
     * @param calendars Holiday calendars for business day adjustment. An empty input means weekends only.
     * @return A generated schedule
     */
    public static ArrayList<LocalDate> generate(LocalDate start, Period tenor,
                                                Period freq, Roll roll, BusDayAdj adj,
                                                String[] calendars) {
        // get end date
        LocalDate end = getUnAdjEndDate(start, tenor, roll);
        boolean adjToEom = (roll == Roll.BACKWARD_EOM || roll == Roll.FORWARD_EOM) && isDayEom(start);
        return generate(start, end, freq, roll, adj, calendars);
    }
}