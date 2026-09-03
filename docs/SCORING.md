# Scoring rules

These are initial personal-preference heuristics, not scientifically validated
safety thresholds. Adjust them in `ActivityScorer`; protect changes with tests.

## Whole day versus best window

1. Select the date in Europe/Madrid. Keep only complete daylight hours, entirely
   inside sunrise/sunset. Today also excludes already-started hours.
2. Score every eligible hour independently. Divide earned points by the available
   factor maximum, multiply by 100, round to the nearest integer, then apply limits.
3. The **day score** is the arithmetic mean of those capped hourly scores,
   rounded to the nearest integer. It is NOT the maximum window score.
4. Every expected daylight hour must have that activity's required weather inputs.
   Sunrise/sunset bounds also detect truncated forecast edges. Missing values or
   gaps mean no full-day rating; the app must not average only the known good hours.
5. Independently evaluate contiguous three-hour windows. Choose the highest score;
   tied windows choose the earliest. Partial days may still have a complete window.

Sea temperature and wave height are optional, independently: absent, invalid or
inapplicable values earn no points and add nothing to the available maximum.
The UI distinguishes Weather only, Weather + sea, partial sea data, and coverage
that varies by hour. These labels describe inputs, not forecast confidence.

Window ratings use the same mean of capped hourly scores as day ratings, with
interval-wide limits applied afterwards. This keeps the two ratings comparable.
For the window's displayed factors, temperatures/clouds are averages, wind/waves/
rain chance are maxima, and rainfall is summed. A sea summary value is shown only
when present throughout the window; known limiting sea conditions still apply
even if other hours lack sea data. Apply limits to the aggregates too.
Cold-water/freezing/high-heat limits inspect individual hours so
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
Cards and details use the same calculated outlook and best window. Each hourly detail shows its
actual raw points, available maximum, normalized score and any reductions.
Day summaries use the same expected daylight slots; a missing
value in any slot leaves that field Unknown instead of summarizing a partial set.

## Beach (available points scaled to 100)

One warm-weather leisure profile, also usable inland for an outdoor pool or
riverside visit. It does not assess pool temperatures, river conditions or swimming
safety. The name Beach is a compact activity label, not a claim that every town has a beach.

| Factor | Points |
| --- | --- |
| Air temperature | 22–28°C: 20; 18–<22: 14; >28–<32: 12; otherwise: 5 |
| Water temperature | >=20°C: 15; 18–<20: 10; 16–<18: 5; <16: 0 |
| Wave height | <=0.5 m: 20; >0.5–0.8: 14; >0.8–1.2: 6; >1.2: 0 |
| Cloud cover | <=20%: 10; >20–45: 7; >45–70: 3; >70: 0 |
| Wind | Maximum 15 points, using the shared wind rule |
| Rain probability | Maximum 20 points, using the shared rain rule |

Air temperature, cloud cover, wind, rain probability and rainfall are required.
Weather contributes up to 65 raw points; known water temperature adds up to 15,
and known wave height adds up to 20. The available maximum is therefore 65, 80,
85 or 100. For example, 59/65 becomes 91/100 before condition limits.
Missing data is not treated as zero waves or warm water. If sea data fails or
ends earlier than weather data, the remaining weather-only estimates are labelled.

Each town has one weather point and, where applicable, one named marine reference.
Inland towns make no marine request. No beach-specific shelter adjustment is inferred.

## Hiking (100 points)

General walking/hiking weather around the selected town, not a route-specific forecast.
Cloudiness is not inherently bad for this activity.

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
