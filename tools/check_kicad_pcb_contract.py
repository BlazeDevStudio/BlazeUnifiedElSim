#!/usr/bin/env python3
"""Check actual parsed KiCad PCB pin/net/track relationships against source demo contract.
This is a connectivity interoperability check, not a full DRC or manufacturing approval.
"""
import sys
from pathlib import Path
import pcbnew

source=Path(sys.argv[1] if len(sys.argv)>1 else 'samples/LED_Placed_Routed.kicad_pcb')
board=pcbnew.LoadBoard(str(source))
refs={fp.GetReference():fp for fp in board.GetFootprints()}
assert set(refs)=={'R1','D1'}, f'unexpected KiCad PCB footprints: {sorted(refs)}'

def pad_map(fp):
    pads={pad.GetNumber():pad.GetNetname() for pad in fp.Pads()}
    assert set(pads)=={'1','2'}, (fp.GetReference(),pads)
    assert all(pads.values()), ('unassigned native KiCad pad net',pads)
    return pads

resistor=pad_map(refs['R1']);led=pad_map(refs['D1'])
assert resistor['2']==led['2'], (resistor,led)
assert resistor['1']!=resistor['2'], resistor
assert led['1']!=led['2'], led
tracks=list(board.GetTracks())
assert len(tracks)==1, f'expected only one user-created copper segment, got {len(tracks)}'
assert tracks[0].GetNetname()==resistor['2'], ('copper attached to wrong net', tracks[0].GetNetname())
print('PASS native KiCad board: 2 placed footprints, 4 pads, 3 nets, 1 correctly assigned F.Cu segment')
