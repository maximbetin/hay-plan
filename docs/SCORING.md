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

Locations are ordered by the selected activity's day score, highest first. Ties use
the stable identifier. Unavailable days go last, even if they have a valid
three-hour window. Beach initially shows the five highest coastal locations; Show all
keeps the coastal ranking first and places ranked inland estimates in a separate section.
Hiking ranks all locations together. Changing date or activity resets an overview to five;
activity changes within details keep the selected location open.

The day headline and every hourly row display the actual internal score out of 100.
The hourly rows are the exact inputs to the day average; missing expected hours
remain visible as Unavailable. The best three-hour span is highlighted in the list.
Cards and details use the same calculated outlook and best window. Each hourly detail shows its
actual raw points, available maximum, normalized score and any reductions.
Day summaries use the same expected daylight slots; a missing
value in any slot leaves that field Unknown instead of summarizing a partial set.

## Scoring philosophy

The score describes how inviting the weather is for the selected activity, not merely
whether going outside is possible. A perfect score therefore requires every weighted
factor to be in its best band. Ordinary compromises lose points, while a condition that
should rule out an Excellent label applies a rating limit after the weighted calculation.

All non-marine inputs are required: air and apparent temperature, humidity, cloud cover,
sustained wind, gusts, rain probability, rainfall amount, visibility, UV index and WMO
weather code. Missing or invalid values make that hour unavailable rather than silently
assuming favorable weather.

## Beach (weather maximum 70; optional marine maximum 30)

One combined swimming/sunbathing profile, also usable inland as an explicitly labelled
weather-only outdoor-leisure estimate. It does not assess flags, currents, water quality,
pool temperature or river conditions.

| Factor | Maximum | Best band and reductions |
| --- | ---: | --- |
| Apparent temperature | 15 | 22–27°C: 15; 19–<22 or >27–30: 12; 16–<19 or >30–33: 7; 12–<16 or >33–<35: 3; otherwise 0 |
| Water temperature, when known | 12 | >=21°C: 12; 19–<21: 9; 17–<19: 5; otherwise 0 |
| Wave height, when known | 18 | <=0.4 m: 18; >0.4–0.7: 13; >0.7–1.0: 7; >1.0–1.2: 3; otherwise 0 |
| Cloud cover | 10 | <=20%: 10; >20–45: 7; >45–70: 4; >70–90: 2; >90: 0 |
| Sustained wind | 8 | Shared wind bands below |
| Wind gusts | 7 | Shared gust bands below |
| Rain probability | 10 | Shared probability bands below |
| Rainfall amount | 10 | Shared rainfall bands below |
| Relative humidity | 5 | Shared humidity bands below |
| Visibility | 5 | Shared visibility bands below |

The weather-only maximum is 70. Known water and waves independently add 12 and 18
available points. Each hour is normalized against only the available maximum, so missing
marine data is not invented as calm or warm; coverage remains clearly labelled.

## Hiking (100 points)

General outdoor walking/hiking weather for the town and nearby area. This intentionally
covers a stroll, an informal local route or an ordinary nearby hike without pretending to
model every path. It is not an exact trail, altitude or mountain forecast.

| Factor | Maximum | Best band and reductions |
| --- | ---: | --- |
| Apparent temperature | 25 | 16–22°C: 25; 13–<16 or >22–25: 22; 10–<13 or >25–28: 17; 5–<10 or >28–31: 10; 0–<5 or >31–<35: 4; otherwise 0 |
| Rain probability | 15 | Shared probability bands below |
| Rainfall amount | 15 | Shared rainfall bands below |
| Sustained wind | 10 | Shared wind bands below |
| Wind gusts | 10 | Shared gust bands below |
| Cloud cover | 15 | <=20%: 15; >20–45: 12; >45–70: 8; >70–90: 4; >90: 0 |
| Relative humidity | 5 | Shared humidity bands below |
| Visibility | 5 | Shared visibility bands below |

Marine conditions do not affect Hiking. Cloudiness now affects the comfort score, and an
overcast WMO condition cannot be labelled Excellent even if every other input is ideal.

## Shared point bands and rating limits

- Sustained wind: <=10 km/h earns 100% of its points; >10–15 earns 80%; >15–22
  earns 50%; >22–30 earns 20%; >30–35 earns 10%; above 35 earns 0.
- Gusts: <=20 km/h earns 100%; >20–30 earns 80%; >30–40 earns 50%; >40–50
  earns 20%; >50–60 earns 10%; above 60 earns 0.
- Rain probability: <=5% earns 100%; >5–15 earns 80%; >15–30 earns 55%;
  >30–50 earns 25%; above 50 earns 0.
- Rainfall: exactly 0 mm earns 100%; >0–0.1 earns 90%; >0.1–0.3 earns 65%;
  >0.3–<1 earns 30%; 1–<3 earns 10%; >=3 earns 0.
- Humidity: 35–70% earns 100%; 25–<35 or >70–80 earns 80%; 20–<25 or
  >80–90 earns 40%; more extreme values earn 20%.
- Visibility: >=10 km earns 100%; 5–<10 earns 80%; 3–<5 earns 50%; 1–<3
  earns 20%; below 1 km earns 0.

Limits prevent a hazardous or clearly compromised hour/window from being averaged into
an implausibly high label. Among them: thunderstorms, freezing or severe precipitation,
wind above 35 km/h or gusts above 60 km/h cap at Poor; fog, moderate precipitation,
rain >=1 mm, very low visibility or gusts above 50 km/h cap at Fair; lighter rain,
reduced visibility, cold/hot apparent temperature or gusts above 40 km/h cap at Good.
Overcast conditions and very high UV cap at Very Good. Extreme UV caps at Good.
The existing marine limits remain: waves >1.2 m cap Beach at Poor, waves >0.8 m cap
at Good, and water below 16°C caps at Fair. Limits cannot improve a score; the lowest
applicable cap wins.

## API alignment and limitations

Weather and marine series are joined by timestamp, never by array position.
Open-Meteo precipitation and gusts describe the preceding hour: values timestamped
12:00 belong to the app's 11:00–12:00 interval. Instantaneous values such as apparent
temperature, cloud cover, humidity, visibility and UV remain on their stated timestamp.
Missing/non-finite values stay unknown. Never extrapolate marine variables into later dates.

Ratings estimate comfort, not safety. Beach flags, local shelter, tides, rip
currents, UV, lightning warnings, elevation differences, trail surfaces and route
hazards are outside this version. Long-range windows are tentative and labelled
as an outlook in the UI.
