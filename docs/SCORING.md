# Scoring rules

These are initial personal-preference heuristics, not scientifically validated
safety thresholds. Adjust them in `ActivityScorer`; protect changes with tests.

## Whole day versus best window

1. Select the date in Europe/Madrid. Keep only complete daylight hours, entirely
   inside sunrise/sunset. Today also excludes already-started hours.
2. Score every eligible hour independently, applying the limits below.
3. The **day score** is the arithmetic mean of those capped hourly scores,
   rounded to the nearest integer. It is NOT the maximum window score.
4. Every expected daylight hour must have that activity's required inputs.
   Sunrise/sunset bounds also detect truncated forecast edges. Missing values or
   gaps mean no full-day rating; the app must not average only the known good hours.
5. Independently evaluate contiguous three-hour windows. Choose the highest score;
   tied windows choose the earliest. Partial days may still have a complete window.

Window ratings use the same mean of capped hourly scores as day ratings, with
interval-wide limits applied afterwards. This keeps the two ratings comparable.
For the window's displayed factors, temperatures/clouds are averages, wind/waves/
rain chance are maxima, and rainfall is summed. Apply the limits to these
aggregates too. Cold-water/freezing/high-heat limits inspect individual hours so
an uncomfortable extreme cannot disappear into the average. Factor values are
window summaries; points are assigned per hour, not to those displayed averages.

A 12-hour day with three excellent hours and nine poor hours is a poor/fair day
with a potentially excellent window, not an excellent day. Day warnings indicate
limiting conditions during some hours; they are not claims that every hour is unsafe.

The rating scale is Poor 0–19, Fair 20–39, Good 40–59, Very Good 60–79,
Excellent 80–100. Detail views also count hours rated Good or better (score >= 40).

## Inspecting the score

The day headline and every hourly row display the actual internal score out of 100.
The hourly rows are the exact inputs to the day average; missing expected hours
remain visible as Unavailable. The best three-hour span is highlighted in the list.
Tap the day score for average factor-point contributions and the mean points lost
to condition limits; their difference, rounded, equals the displayed day score.
Tap an hour for its measured values, awarded points and any applied limits. Tap the
best window for its summarized conditions. The rule thresholds below are unchanged.

## Beach (100 points)

One profile for a pleasant beach visit that may include swimming. No separate
sunbathing/swimming scores or implied protection from UV or currents.

| Factor | Points |
| --- | --- |
| Air temperature | 22–28°C: 20; 18–<22: 14; >28–<32: 12; otherwise: 5 |
| Water temperature | >=20°C: 15; 18–<20: 10; 16–<18: 5; <16: 0 |
| Wave height | <=0.5 m: 20; >0.5–0.8: 14; >0.8–1.2: 6; >1.2: 0 |
| Cloud cover | <=20%: 10; >20–45: 7; >45–70: 3; >70: 0 |
| Wind | Maximum 15 points, using the shared wind rule |
| Rain probability | Maximum 20 points, using the shared rain rule |

Every weather factor, rainfall amount, wave height and water temperature is
required. Unknown sea data makes Beach unavailable; it cannot silently become a
weather-only rating. Gijón defaults to San Lorenzo; selecting another beach uses
that beach's own weather and marine coordinates. No shelter or local hazard
adjustment is inferred from the beach name.

## Hiking (100 points)

General outdoor/hiking/walking weather around a town; not a route-specific forecast.
No trail catalog is needed. Cloudiness is not inherently bad for this activity.

| Factor | Points |
| --- | --- |
| Air temperature | 15–24°C: 40; 10–<15: 30; >24–<29: 28; 5–<10 or 29–<32: 15; otherwise: 0 |
| Wind | Maximum 25 points, using the shared wind rule |
| Rain probability | Maximum 35 points, using the shared rain rule |

Air temperature, wind, rain probability and rainfall amount are required.
Marine variables and cloud cover are not required and do not affect the score.

## Shared point rules and limits

- Wind: <=15 km/h earns all points; >15–25 earns 60%; >25–35 earns 20%; >35 earns 0.
- Rain probability: <=10% earns all points; >10–30 earns 60%; >30–50 earns 25%;
  >50 earns 0. Fractional points round to the nearest integer.
- Rain amount is displayed separately and acts through limits, not bonus points.
- Wind >35 km/h: score capped at 19 (Poor).
- Rain >=3 mm in the scored interval: capped at 19. Otherwise rain >=1 mm or
  probability >50%: capped at 39 (Fair).
- Any air temperature >=35°C: capped at 39.
- Beach waves >1.2 m: capped at 19; otherwise >0.8 m: capped at 59 (Good).
- Any Beach water temperature <16°C: capped at 39.
- Any Hiking air temperature <0°C: capped at 39.

Limits cannot improve a score. When several apply, the lowest cap wins.

## API alignment and limitations

Weather and marine series are joined by timestamp, never by array position.
Open-Meteo precipitation describes the preceding hour: the value timestamped
12:00 belongs to the app's 11:00–12:00 interval. Missing/non-finite values stay
unknown. Never extrapolate marine variables into later dates.

Ratings estimate comfort, not safety. Beach flags, local shelter, tides, rip
currents, UV, lightning warnings, elevation differences, trail surfaces and route
hazards are outside this version. Long-range windows are tentative and labelled
as an outlook in the UI.
