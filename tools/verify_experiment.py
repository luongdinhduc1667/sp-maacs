"""Independently validate stored Figure-3-profile measurements; standard Python only."""
import csv
import json
import math
import statistics
import sys
from pathlib import Path

summary_path = Path(sys.argv[1] if len(sys.argv) > 1 else "output/paper-figure3-517.csv")
with summary_path.open(encoding="utf-8-sig", newline="") as stream:
    summary = list(csv.DictReader(stream))
with Path(str(summary_path) + ".raw.csv").open(encoding="utf-8-sig", newline="") as stream:
    raw = list(csv.DictReader(stream))
metadata = json.loads(Path(str(summary_path) + ".metadata.json").read_text(encoding="utf-8-sig"))
assert len(summary) == 5 and len(raw) == 50
assert metadata["primeBits"] == 517 and metadata["primeCount"] == 3
for row, count, satisfying in zip(summary, [5, 10, 15, 20, 25], [3, 5, 7, 10, 12]):
    samples = [r for r in raw if int(r["policy_attributes"]) == count]
    assert len(samples) == 10 and {int(r["iteration"]) for r in samples} == set(range(1, 11))
    assert int(row["policy_attributes"]) == count
    assert int(row["keygen_attributes"]) == satisfying
    assert int(row["decryption_attributes"]) == satisfying
    assert int(row["runs"]) == 10
    for sample in samples:
        assert sample["backend"] == row["backend"] == "JPBC_2.0.0_TYPE_A1_BLS01"
        assert int(sample["prime_bits"]) == int(row["prime_bits"]) == 517
        assert int(sample["keygen_attributes"]) == int(sample["decryption_attributes"]) == satisfying
        assert sample["key_recovered"] == "true"
        assert int(sample["decrypt_pairings"]) == 2 * satisfying + 1
    fields = [
        ("ca_keygen_ms", "ca_keygen_mean_ms"),
        ("aa_keygen_ms", "aa_keygen_mean_ms"),
        ("abe_encrypt_ms", "abe_encrypt_mean_ms"),
        ("abe_decrypt_ms", "abe_decrypt_mean_ms"),
    ]
    for source, destination in fields:
        values = [float(r[source]) for r in samples]
        assert all(math.isfinite(v) and v > 0 for v in values)
        assert abs(statistics.mean(values) - float(row[destination])) < 0.000002
    assert abs(float(row["keygen_total_mean_ms"]) - float(row["ca_keygen_mean_ms"]) - float(row["aa_keygen_mean_ms"])) < 0.000002
    assert abs(statistics.pstdev(float(r["abe_encrypt_ms"]) for r in samples) - float(row["encrypt_population_sd_ms"])) < 0.000002
    assert abs(statistics.pstdev(float(r["abe_decrypt_ms"]) for r in samples) - float(row["decrypt_population_sd_ms"])) < 0.000002
print("PASS: 50/50 real 517-bit samples; exact Figure 3 axes; all K recovered; means and SD independently verified.")
