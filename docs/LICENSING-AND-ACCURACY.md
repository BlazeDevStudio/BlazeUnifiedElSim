# Library licensing and engineering accuracy

- **Original BlazeBasic:** The included 15 `.kicad_mod` footprints, 9 KiCad symbol definitions, and metadata were authored as minimal reference geometry specifically for this development handoff. They are intentionally not copies of proprietary EveryCircuit, Altium, Arduino branding/UI, official KiCad libraries, or vendor production footprints. Physical package data and pad offsets are preliminary; verify against the selected manufacturer part drawings.
- **KiCad:** Official KiCad libraries have CC-BY-SA 4.0 redistribution terms and an exception for finished designs. Download the official collections through the KiCad project if needed and preserve the license/attribution when redistributing those collections. https://www.kicad.org/libraries/license/
- **Arduino:** Uno R3 and Nano classic pin names/function references come from public hardware documentation, not their firmware cores or logo assets. https://docs.arduino.cc/hardware/uno-rev3 and https://docs.arduino.cc/hardware/nano
- **ESP32:** Pin names and caution notes are derived from the ESP32-DevKitC V4 documentation. Strapping-pin and ADC2/Wi-Fi limitations are reference warnings, not physical safety proof. https://docs.espressif.com/projects/esp-dev-kits/en/latest/esp32/esp32-devkitc/user_guide.html
- **Altium:** No Altium libraries or proprietary EDA source have been bundled, cloned, or copied. Native PCB routing tools remain preliminary, not Altium-equivalent.
- **MCU runtime:** Pin planning does not run compiled MCU firmware or act as a hardware electrical source. No Arduino core/toolchain/AVR/ESP32 emulator is part of this build.
- **Numerics:** Nine component classes now have tested simulation behavior; I/L use idealized models. Their models are educational/approximate. No physical circuit or board is safety-certified.

- **ESP8266 NodeMCU:** D-pin-to-GPIO mapping and GPIO16 limitations from the NodeMCU documentation. https://nodemcu.readthedocs.io/en/dev/modules/gpio/
