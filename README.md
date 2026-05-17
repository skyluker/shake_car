# ShakeCar

Aplikacja Android do diagnostyki zawieszenia samochodu przez pomiar wstrząsów telefonu przyczepionego do nadwozia.

## Co robi

1. Zbiera dane z akcelerometru (`TYPE_LINEAR_ACCELERATION`) i żyroskopu z maksymalną częstotliwością obsługiwaną przez urządzenie (zwykle 100–500 Hz).
2. Wykonuje **FFT** zarejestrowanego sygnału pionowego.
3. Liczy wskaźniki diagnostyczne:
   - **RMS** przyspieszenia (m/s²),
   - **RMS ważone Wk wg ISO 2631-1** — wskaźnik dyskomfortu jazdy,
   - **Crest factor** — wykrywa nagłe stuki/luzy,
   - **Częstotliwość rezonansowa nadwozia** (zwykle 1–2 Hz),
   - **Współczynnik tłumienia ζ** metodą *half-power bandwidth* — kondycja amortyzatorów,
   - **Energię w sześciu pasmach** istotnych dla diagnostyki zawieszenia.
4. Generuje zalecenia naprawcze na bazie reguł.
5. Zapisuje sesję do bazy Room (przebieg, opony, nawierzchnia) — można porównywać kondycję pojazdu w czasie.

## Pasma częstotliwości — co oznaczają

| Pasmo | Zakres | Co reprezentuje | Co świadczy o zużyciu |
|---|---|---|---|
| Motion sickness | 0.1–0.5 Hz | Bujanie, kolebanie | Niedotłumiony amortyzator + długie fale na drodze |
| **Body bounce** | 0.8–2.5 Hz | Rezonans masy resorowanej (nadwozia) | **Pik wąski i wysoki = zużyte amortyzatory** (niski ζ) |
| Discomfort | 4–8 Hz | Najgorzej tolerowane przez człowieka, ISO 2631 | Wskazuje subiektywny dyskomfort |
| **Wheel hop** | 10–15 Hz | Rezonans masy nieresorowanej | Wzrost = uszkodzone tuleje wahaczy, opony, łożyska |
| Wheel imbalance | 15–25 Hz | Drgania na prędkościach autostradowych | Niewyważone koło, bita felga |
| Rattles | 25–40 Hz | Stuki i klekoty | Luzy w łącznikach, końcówkach |

Współczynnik tłumienia interpretacja:
- **ζ ≈ 0.25–0.35** — nowoczesny amortyzator w normie
- **ζ ≈ 0.10–0.20** — zużycie, czas na kontrolę
- **ζ < 0.10** — duże zużycie, wymiana

## Procedura pomiaru (wymagane stabilne warunki)

1. Telefon przyczep stabilnie do nadwozia (uchwyt na konsoli, taśma do podsufitki, mocowanie do progu w bagażniku — im bliżej środka pojazdu tym lepiej).
2. Wybierz drogę o **w miarę jednolitej fakturze** (asfalt, kostka, makadam) i jedź **stałą prędkością ~60–90 km/h przez minimum 60 sekund**.
3. Powtórz pomiar przy podobnych warunkach (ciśnienie opon, obciążenie) co ~10 000 km — porównanie z własną historią daje najwiarygodniejszy obraz.

## Ograniczenia uczciwie wymienione

- **Brak referencji absolutnej** — różne modele aut mają różne kalibracje fabryczne. Aplikacja jest najsilniejsza w trybie **trend dla własnego pojazdu**.
- **Wpływ opon, ciśnienia, obciążenia, nawierzchni jest istotny** — wszystkie te zmienne wpływają na widmo. Dlatego sesje są opisywane metadanymi.
- Telefon nie jest sztywno połączony z osią nieresorowaną — pasmo 10–15 Hz jest tłumione przez nadwozie. Diagnostyka kół jest pośrednia.
- Filtr Wk z ISO 2631 jest tu uproszczoną aproksymacją (krzywa pasmowa). Dokładna implementacja wymaga IIR z normą.

## Architektura

```
app/
├── domain/        # SignalAnalyzer, FrequencyBand, Diagnosis - czysta logika
├── sensor/        # SensorRecorder (Flow), RecordingController, RecordingService
├── data/          # Room - VehicleEntity, SessionEntity, repozytorium
└── ui/            # Compose - MainActivity, ekrany, ViewModel-e
```

## Stos technologiczny

- Kotlin 2.0 + Jetpack Compose (Material 3)
- Room 2.6 do persystencji
- JTransforms do FFT
- Coroutines + Flow do streamowania próbek

## Roadmapa

- [ ] Eksport sesji do CSV/JSON (na potrzeby crowdsourcingu)
- [ ] Bazka referencyjna sygnatur dla popularnych modeli (przesyłana opcjonalnie przez użytkowników)
- [ ] Klasyfikator ML (random forest na cechach pasm) zamiast samych reguł
- [ ] Korekta orientacji telefonu (kwaterion, fuzja akcelerometr+żyroskop)
- [ ] Włączenie GPS i automatyczne odsiewanie segmentów ze zmianą prędkości
- [ ] Wykres trendu w czasie (np. ζ vs. przebieg)

## Licencja

MIT
