#!/usr/bin/env python3
"""Independent known-demo electrical net partitions from native CircuitLab demos.

This deliberately does not trust KiCad's net names (which are implementation choices).
It compares the actual KiCad 9 CLI XML output to the circuit's expected pin groups.
Pin numbering has deliberately independent mappings: LED K=1/A=2, V -=2/+=1.
Not a substitute for validating arbitrary generated schematics or ERC/PCB DRC.
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

EXPECTED = {
    'LED_Current_Limiter': [
        {'D1.1', 'V1.2'}, # grounded diode cathode, DC source minus
        {'D1.2', 'R1.2'}, # diode anode / resistor second terminal
        {'R1.1', 'V1.1'}, # resistor first terminal / DC source plus
    ],
    'RC_Charging': [
        {'AC1.2', 'C1.2'},
        {'C1.1', 'R1.2'},
        {'AC1.1', 'R1.1'},
    ],
    'Powered_Opamp_Rail_Limiter': [
        {'A1.2', 'R1.2', 'V1.2', 'V2.1', 'V3.2'},
        {'A1.1', 'V3.1'},
        {'A1.4', 'V1.1'},
        {'A1.5', 'V2.2'},
        {'A1.3', 'R1.1'},
    ],
}


def extract_connections(path: Path):
    root = ET.parse(path).getroot()
    components = {node.attrib['ref'] for node in root.findall('./components/comp')}
    groups = []
    all_pins = set()
    for net in root.findall('./nets/net'):
        pins = set()
        for node in net.findall('node'):
            pin = f"{node.attrib['ref']}.{node.attrib['pin']}"
            if pin in all_pins:
                raise ValueError(f'{path.name}: repeated electrical pin in multiple nets: {pin}')
            all_pins.add(pin)
            pins.add(pin)
        if pins:
            groups.append(pins)
    return components, groups


def verify(name: str, path: Path):
    known = EXPECTED[name]
    actual_refs, observed = extract_connections(path)
    expected_refs = {s.split('.')[0] for group in known for s in group}
    if actual_refs != expected_refs:
        raise ValueError(f'{name}: components changed: {actual_refs} versus {expected_refs}')
    actual_groups = {frozenset(g) for g in observed}
    expected_groups = {frozenset(g) for g in known}
    if actual_groups != expected_groups:
        raise ValueError(f'{name}: KiCad physical nets differ: {observed}; expected: {known}')
    print(f'PASS KiCad pin/net equivalence {name}: {len(actual_groups)} nets, {sum(map(len,actual_groups))} pins')


def main(directory: Path):
    for name in EXPECTED:
        path = directory / f'{name}.xml'
        if not path.is_file():
            raise ValueError(f'Missing KiCad CLI netlist: {path}')
        verify(name, path)


if __name__ == '__main__':
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else Path('build/kicad-interop'))