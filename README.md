# 🎲 Dau - App Android

Una aplicació Android senzilla i elegant per llançar un dau virtual.

## Característiques

- **Interfície moderna** amb Jetpack Compose i Material 3
- **Animacions fluides** de rotació i escala quan es llança el dau
- **Disseny atractiu** amb gradients i colors vibrants
- **Interacció dual**: toca el dau directament o utilitza el botó

## Requisits

- Android Studio Hedgehog (2023.1.1) o superior
- JDK 17
- Android SDK 34

## Instal·lació

1. Obre Android Studio
2. Selecciona "Open" i navega fins al directori `DiceApp`
3. Espera que Gradle sincronitzi el projecte
4. Connecta un dispositiu Android o inicia un emulador
5. Prem "Run" (▶️)

## Estructura del projecte

```
DiceApp/
├── app/
│   ├── src/main/
│   │   ├── java/com/diceapp/
│   │   │   └── MainActivity.kt      # Activitat principal amb Compose
│   │   ├── res/
│   │   │   ├── values/
│   │   │   │   ├── strings.xml
│   │   │   │   ├── colors.xml
│   │   │   │   └── themes.xml
│   │   │   └── drawable/
│   │   │       └── ic_launcher_foreground.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Tecnologies

- **Kotlin** - Llenguatge de programació
- **Jetpack Compose** - UI toolkit modern
- **Material 3** - Sistema de disseny
- **Coroutines** - Gestió d'animacions asíncrones

## Com funciona

1. L'usuari toca el dau o prem el botó "Tirar Dau"
2. S'inicia una animació de rotació (720°) i escala
3. El valor del dau canvia ràpidament 10 vegades per simular el llançament
4. Es mostra el resultat final (1-6)

## Llicència

MIT License

