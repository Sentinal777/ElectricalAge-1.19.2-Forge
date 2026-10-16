"""Run pinned-baseline source reconciliation without overwriting release evidence.

The previous 0.3 audit had historical, hard-coded partial/missing classifications;
it is preserved in docs/history/0.3.0-audit_coverage.py. Actual release behavior
and test results are maintained separately from registration discovery.
"""
from pathlib import Path
import runpy
runpy.run_path(str(Path(__file__).with_name('audit_baseline_coverage.py')), run_name='__main__')
