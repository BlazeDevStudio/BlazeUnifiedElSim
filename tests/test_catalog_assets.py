"""Distribution/catalog contract tests: real package assets, no web or mock catalog."""
import json
import math
from pathlib import Path
import unittest
from copy import deepcopy

ROOT = Path(__file__).resolve().parents[1]
CAT = ROOT / 'app/src/main/assets/catalog'
ASSETS = ROOT / 'app/src/main/assets'
PIN_COUNTS = {'GND': 1, 'QNPN': 3, 'MNMOS': 3, 'EVCVS': 4, 'OPAMP': 5}
MODELS = {'V', 'AC', 'R', 'C', 'LED', 'SW', 'GND', 'I', 'L', 'D', 'QNPN', 'MNMOS', 'EVCVS', 'OPAMP'}


def load_assets():
    return [json.loads((CAT / file).read_text('utf-8'))
            for file in ('electronics.json', 'footprints.json', 'mcu_pinouts.json')]


def validate(parts, footprints, mcus):
    if any(d.get('schema') != 2 for d in (parts, footprints, mcus)):
        raise ValueError('schema must be v2')
    if mcus['mode'] != 'reference-and-planning-only':
        raise ValueError('MCUs are reference only')
    assert_unique(parts['presets'], 'id')
    assert_unique(footprints['footprints'], 'id')
    assert_unique(mcus['boards'], 'id')
    fp = {e['id']: e for e in footprints['footprints']}
    if {e['simulatorModel'] for e in parts['presets']} != MODELS:
        raise ValueError('unsupported/missing numerical models')
    for e in parts['presets']:
        if e['simulatorModel'] not in MODELS or not math.isfinite(e['defaultValue']):
            raise ValueError('unknown or invalid electrical model')
        if not (math.isfinite(e['frequency']) and e['frequency'] > 0):
            raise ValueError('invalid frequency')
        for ref in e['footprints']:
            if ref not in fp or e['simulatorModel'] not in fp[ref]['supported']:
                raise ValueError('unresolved or incompatible footprint')
    for e in footprints['footprints']:
        pins = e['padToTerminal']
        geo = e['padOffsetsMm']
        if not pins or set(pins) != set(geo):
            raise ValueError('missing pad geometry or mapping')
        for component in e['supported']:
            if component not in MODELS:
                raise ValueError('unknown simulator model in footprint')
            if set(pins.values()) != set(range(PIN_COUNTS.get(component, 2))):
                raise ValueError('mismatched terminal index for ' + e['id'])
        for point in geo.values():
            if any(not math.isfinite(point[k]) or abs(point[k]) > 100 for k in ('x','y')):
                raise ValueError('invalid physical geometry')
        path = e['localKiCadFile']
        if e['id'].startswith('BlazeBasic:'):
            expected = 'kicad/BlazeBasic.pretty/' + e['id'].split(':', 1)[1] + '.kicad_mod'
            if path != expected:
                raise ValueError('local footprint identity mismatch')
        if path is not None:
            if not path.startswith('kicad/BlazeBasic.pretty/') or '..' in path:
                raise ValueError('untrusted local file path')
            if not (ASSETS / path).is_file():
                raise ValueError('absent KiCad file: ' + path)
    for b in mcus['boards']:
        assert_unique(b['pins'], 'name')
        if not (0 < b['logicVoltageV'] <= 5.0):
            raise ValueError('invalid MCU I/O voltage')


def assert_unique(items, key):
    keys = [e[key] for e in items]
    if len(set(keys)) != len(keys):
        raise ValueError('duplicate ' + key)


class CatalogAssetTests(unittest.TestCase):
    def setUp(self):
        self.source = load_assets()

    def test_installed_v2_assets_cross_reference(self):
        validate(*self.source)

    def test_expected_library_coverage_not_fake_models(self):
        electronics, footprint, mcu = self.source
        self.assertEqual(len(electronics['presets']), 97)
        self.assertEqual(len(footprint['footprints']), 23)
        self.assertEqual(len(mcu['boards']), 4)
        self.assertEqual({p['simulatorModel'] for p in electronics['presets']}, MODELS)

    def test_reject_old_schema(self):
        src = deepcopy(self.source); src[0]['schema'] = 1
        with self.assertRaises(ValueError): validate(*src)

    def test_reject_duplicate_presets(self):
        src = deepcopy(self.source); src[0]['presets'].append(src[0]['presets'][0])
        with self.assertRaises(ValueError): validate(*src)

    def test_reject_unknown_solver_model(self):
        src = deepcopy(self.source); src[0]['presets'][0]['simulatorModel'] = 'FAKE_555'
        with self.assertRaises(ValueError): validate(*src)

    def test_reject_unresolved_footprints(self):
        src = deepcopy(self.source); src[0]['presets'][0]['footprints'].append('Missing:Part')
        with self.assertRaises(ValueError): validate(*src)

    def test_reject_polarity_swap_or_missing_pad(self):
        src = deepcopy(self.source)
        diode = next(e for e in src[1]['footprints'] if e['id'] == 'BlazeBasic:D_DO35_7.62')
        diode['padToTerminal']['2'] = 1
        with self.assertRaises(ValueError): validate(*src)

    def test_reject_invalid_geometry(self):
        src = deepcopy(self.source)
        src[1]['footprints'][0]['padOffsetsMm']['1']['x'] = float('inf')
        with self.assertRaises(ValueError): validate(*src)

    def test_reject_missing_local_file(self):
        src = deepcopy(self.source); src[1]['footprints'][0]['localKiCadFile'] = 'kicad/BlazeBasic.pretty/NOT_THERE.kicad_mod'
        with self.assertRaises(ValueError): validate(*src)

    def test_reject_unverified_esp_runtime_claim(self):
        src = deepcopy(self.source); src[2]['mode'] = 'firmware-emulation'
        with self.assertRaises(ValueError): validate(*src)

    def test_reject_duplicate_mcu_pins(self):
        src = deepcopy(self.source); src[2]['boards'][0]['pins'].append(src[2]['boards'][0]['pins'][0])
        with self.assertRaises(ValueError): validate(*src)

    def test_catalog_data_contains_frequency_and_pad_coordinates(self):
        self.assertTrue(all('frequency' in e for e in self.source[0]['presets']))
        self.assertTrue(all('padOffsetsMm' in e for e in self.source[1]['footprints']))
        self.assertTrue(all(all('header' in p for p in board['pins']) for board in self.source[2]['boards']))


if __name__ == '__main__':
    unittest.main()
