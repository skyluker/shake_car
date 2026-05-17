# ShakeCar

Aplikacja Android do diagnostyki zawieszenia samochodu na podstawie wstrząsów telefonu przyczepionego do nadwozia.

## Co robi

1. Zbiera dane z akcelerometru (`TYPE_LINEAR_ACCELERATION`), grawitacji (`TYPE_GRAVITY`) i żyroskopu z maksymalną częstotliwością obsługiwaną przez urządzenie (zwykle 100–500 Hz).
2. **Korekta orientacji** — pionowa składowa przyspieszenia obliczana jest jako rzut wektora `acc` na kierunek wektora grawitacji `g/|g|`. Telefon nie musi leżeć płasko ani być idealnie zorientowany.
3. **Segmentacja prędkości GPS** — `FusedLocationProviderClient` raz na 0.5 s; do analizy trafiają tylko fragmenty o stabilnej prędkości (≥30 km/h, σ ≤ 5 km/h w oknie 5 s). Postoje, hamowania i przyspieszenia są odsiewane.
4. Wykonuje **FFT** zaakceptowanego sygnału pionowego.
5. **Filtr Wk wg ISO 2631-1:1997** — kaskada 4 sekcji biquad (HP 0.4 Hz, LP 100 Hz, transition 12.5 Hz, upward step 2.37/3.35 Hz), bilinear transform.
6. Liczy wskaźniki diagnostyczne:
   - **RMS** przyspieszenia (m/s²),
   - **RMS Wk wg ISO 2631-1** — wskaźnik dyskomfortu (awc),
   - **Crest factor** — wykrywa nagłe stuki/luzy,
   - **Częstotliwość rezonansowa nadwozia** (1–2 Hz),
   - **Współczynnik tłumienia ζ** metodą *half-power bandwidth*,
   - Energia w 6 pasmach diagnostycznych.
7. **Eksport CSV/JSON** przez Storage Access Framework — surowe próbki + metadane + wynik analizy. Można otworzyć w Excelu / R / pandas i kalibrować klasyfikator offline.
8. Zapisuje sesję do bazy Room (przebieg, opony, nawierzchnia) — można porównywać kondycję pojazdu w czasie.

## Pasma częstotliwości — co oznaczają

| Pasmo | Zakres | Co reprezentuje | Co świadczy o zużyciu |
|---|---|---|---|
| Motion sickness | 0.1–0.5 Hz | Bujanie, kolebanie | Niedotłumiony amortyzator |
| **Body bounce** | 0.8–2.5 Hz | Rezonans masy resorowanej (nadwozia) | **Wąski wysoki pik = zużyte amortyzatory** (niski ζ) |
| Discomfort | 4–8 Hz | Najgorzej tolerowane przez człowieka | Subiektywny dyskomfort |
| **Wheel hop** | 10–15 Hz | Rezonans masy nieresorowanej | Wzrost = uszkodzone tuleje, opony, łożyska |
| Wheel imbalance | 15–25 Hz | Drgania na prędkościach autostradowych | Niewyważone koło, bita felga |
| Rattles | 25–40 Hz | Stuki i klekoty | Luzy w łącznikach, końcówkach |

Współczynnik tłumienia interpretacja:
- **ζ ≈ 0.25–0.35** — nowoczesny amortyzator w normie
- **ζ ≈ 0.10–0.20** — zużycie, czas na kontrolę
- **ζ < 0.10** — duże zużycie, wymiana

## Format eksportu

### CSV (surowe próbki)
```
timestampNs,accX,accY,accZ,gravX,gravY,gravZ,gyrX,gyrY,gyrZ,speedKmh,gpsAccM
```

### JSON (metadane + analiza)
```json
{
  "schemaVersion": 1,
  "session": { "id": 1, "startedAt": ..., "mileageKm": 87000, "roadType": "asfalt" },
  "vehicle": { "make": "Skoda", "model": "Octavia", "year": 2018, "tireSpec": "205/55R16" },
  "analysis": {
    "sampleRateHz": 198, "rmsWeightedIso2631": 0.42, "dampingRatio": 0.18, ...
    "bands": [ { "name": "BODY_BOUNCE", "lowHz": 0.8, "highHz": 2.5, "energy": 0.00135 }, ... ]
  }
}
```

## Procedura pomiaru

1. Telefon przyczep stabilnie do nadwozia (uchwyt na konsoli, taśma do podsufitki, mocowanie do progu w bagażniku — im bliżej środka pojazdu tym lepiej).
2. Włącz GPS i zezwól aplikacji na precyzyjną lokalizację.
3. Wybierz drogę o w miarę jednolitej fakturze (asfalt, kostka, makadam) i jedź **stałą prędkością ~60–90 km/h przez minimum 60 sekund**.
4. Aplikacja sama odsieje fragmenty postojów i zmian prędkości — w wynikach zobaczysz "Wykorzystane próbki: X%".
5. Powtarzaj pomiary co ~10 000 km — porównanie z własną historią daje najwiarygodniejszy obraz kondycji.

## Ograniczenia

- **Brak referencji absolutnej** — różne modele aut mają różne kalibracje fabryczne. Aplikacja jest najsilniejsza w trybie *trend dla własnego pojazdu*.
- **Wpływ opon, ciśnienia, obciążenia, nawierzchni** — wszystkie te zmienne wpływają na widmo. Dlatego sesje są opisywane metadanymi.
- Telefon nie jest sztywno połączony z osią nieresorowaną — pasmo 10–15 Hz jest tłumione przez nadwozie. Diagnostyka kół jest pośrednia.
- Filtr Wk implementowany w domenie cyfrowej z bilinear transform bez prewarpingu — dokładność dobra w paśmie 0.5–30 Hz.

## Architektura

```
app/
├── domain/        # SignalAnalyzer, IsoWkFilter, Biquad, FrequencyBand, Diagnosis
├── sensor/        # SensorRecorder (Flow + GPS), RecordingController, RecordingService
├── data/          # Room, SessionRepository, SessionExporter
└── ui/            # Compose - 4 ekrany, ViewModel-e
```

## Stos technologiczny

- Kotlin 2.0 + Jetpack Compose (Material 3)
- Room 2.6 do persystencji
- JTransforms do FFT
- Coroutines + Flow do streamowania próbek
- Play Services Location (FusedLocationProviderClient)

## Roadmapa

- [ ] Klasyfikator ML (random forest na cechach pasm) zamiast samych reguł
- [ ] Fuzja akcelerometr+żyroskop do precyzyjniejszej rotacji (kwaterion AHRS)
- [ ] Crowdsourcing sygnatur — opcjonalna anonimowa wymiana sesji JSON
- [ ] Wykres trendu w czasie (np. ζ vs. przebieg dla konkretnego pojazdu)
- [ ] Detektor charakteru drogi (opon, asfalt, kostka, dziura) z autosegmentacji

## Licencja

MIT
