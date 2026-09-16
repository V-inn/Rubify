#!/usr/bin/env python3
"""Builds Rubify's pinyin dictionary assets from pinned open data sources.

Outputs, in app/src/main/assets/pinyin/:
  chars.tsv     char <TAB> default reading <TAB> frequency, by code point
  words.tsv     word <TAB> frequency [<TAB> pinyin], in UTF-16 order (pinyin
                only when it differs from the chars' default readings)
  LICENSES.txt  notices of the sources (all MIT)

Sources (downloaded into tools/pinyin-data/.cache/, not committed):
  mozillazg/pinyin-data         per-character readings
  mozillazg/phrase-pinyin-data  per-phrase readings (polyphonic characters)
  fxsjy/jieba                   word frequencies for segmentation

Usage: python3 tools/pinyin-data/build_pinyin_assets.py
Python 3.8+, standard library only. Output is deterministic.
"""

import pathlib
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
CACHE = pathlib.Path(__file__).resolve().parent / ".cache"
OUT = ROOT / "app/src/main/assets/pinyin"

PINYIN_DATA = "https://raw.githubusercontent.com/mozillazg/pinyin-data/923b108dc5d45dee061324c011b478fb649f8b73/"
PHRASE_DATA = "https://raw.githubusercontent.com/mozillazg/phrase-pinyin-data/cee0ed6e6e4898580cafd2bd5e3723e20b214aa0/"
JIEBA = "https://raw.githubusercontent.com/fxsjy/jieba/67fa2e36e72f69d9134b8a1037b83fbb070b9775/"

SOURCES = {
    "chars.txt": PINYIN_DATA + "pinyin.txt",
    "chars-LICENSE": PINYIN_DATA + "LICENSE",
    "phrases.txt": PHRASE_DATA + "large_pinyin.txt",
    "phrases-LICENSE": PHRASE_DATA + "LICENSE",
    "jieba-dict.txt": JIEBA + "jieba/dict.txt",
    "jieba-LICENSE": JIEBA + "LICENSE",
}

# Words below this jieba frequency are dropped: keeps ~99% of the corpus
# mass with a third of the entries.
MIN_WORD_FREQ = 5
# Phrases missing from jieba but read differently from their characters'
# defaults still need to compete in segmentation; they get this frequency.
PHRASE_ONLY_FREQ = 3
# Longest word the runtime segmenter looks up (WordSegmenter.MAX_WORD_LENGTH).
MAX_WORD_LENGTH = 8

# Default reading when a character is a word on its own, where the source's
# first-listed reading is not the common standalone one. Mostly guided by
# jieba's part-of-speech tag for the single character (u* = particle).
STANDALONE_READINGS = {
    "地": "de",    # structural particle (认真地), tag uv; 地方/土地 are words
    "得": "de",    # complement particle (跑得快), tag ud; 得到/得去 are words
    "长": "cháng", # adjective "long" (很长), tag a; 长大/校长 are words
    "教": "jiāo",  # verb "teach" (教我); 教室/教育 are words
    "切": "qiē",   # verb "cut"; 一切/亲切 are words
    "似": "sì",    # adverb, tag d; 似的 is a word
    "尽": "jìn",   # verb "exhaust" (尽力); 尽管/尽量 are words
}

# Common words missing from jieba (or too rare there to win segmentation)
# whose reading differs from the characters' defaults: word -> (frequency,
# pinyin). The frequency only has to beat the word's own characters taken
# separately. Add entries only for verified, common usage: rare classical
# phrases from the phrase source (都会 dūhuì, 倒是 dǎoshì) are deliberately
# left at PHRASE_ONLY_FREQ so they cannot hijack ordinary text.
EXTRA_WORDS = {
    "长得": (1000, "zhǎng de"),  # 他长得很高
    "长高": (1000, "zhǎng gāo"),
    "还书": (1000, "huán shū"),
    "还钱": (1000, "huán qián"),
}

# 一 and 不 are stored with their citation tone. The phrase source applies
# tone sandhi to some entries (一个 yí gè) but not others (一次 yī cì);
# mixing both would look arbitrary on screen.
CITATION_TONES = {"一": {"yí": "yī", "yì": "yī"}, "不": {"bú": "bù"}}


def fetch(name):
    path = CACHE / name
    if not path.exists():
        CACHE.mkdir(parents=True, exist_ok=True)
        print(f"downloading {SOURCES[name]}")
        with urllib.request.urlopen(SOURCES[name], timeout=120) as response:
            path.write_bytes(response.read())
    return path.read_text(encoding="utf-8")


def is_hanzi(text):
    return bool(text) and all(
        c == "\u3007"  # ideographic zero
        or "\u3400" <= c <= "\u9fff"  # Extension A and URO
        or "\uf900" <= c <= "\ufaff"  # compatibility ideographs
        or "\U00020000" <= c <= "\U0003134f"  # Extensions B to H
        for c in text
    )


def utf16_key(text):
    # Kotlin/Java String order is UTF-16 code unit order, not code point order.
    return text.encode("utf-16-be")


def load_char_readings():
    readings = {}
    for line in fetch("chars.txt").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        code, rest = line.split(":", 1)
        char = chr(int(code.strip()[2:], 16))
        if is_hanzi(char):
            readings[char] = rest.split("#")[0].strip().split(",")[0]
    readings.update(STANDALONE_READINGS)
    return readings


def load_phrase_readings():
    phrases = {}
    for line in fetch("phrases.txt").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        word, rest = line.split(":", 1)
        syllables = rest.split("#")[0].split()
        if not is_hanzi(word) or len(syllables) != len(word) or len(word) > MAX_WORD_LENGTH:
            continue
        phrases[word] = [CITATION_TONES.get(c, {}).get(s, s) for c, s in zip(word, syllables)]
    return phrases


def load_jieba_frequencies():
    frequencies = {}
    for line in fetch("jieba-dict.txt").splitlines():
        word, freq, _tag = line.split(" ")
        if is_hanzi(word) and len(word) <= MAX_WORD_LENGTH:
            frequencies[word] = int(freq)
    return frequencies


def main():
    chars = load_char_readings()
    phrases = load_phrase_readings()
    jieba = load_jieba_frequencies()

    def default_reading(word):
        return [chars.get(c) for c in word]

    words = {}
    for word, freq in jieba.items():
        if len(word) >= 2 and freq >= MIN_WORD_FREQ:
            words[word] = freq
    for word, syllables in phrases.items():
        if word not in words and syllables != default_reading(word):
            words[word] = jieba.get(word, PHRASE_ONLY_FREQ)
    for word, (freq, pinyin) in EXTRA_WORDS.items():
        words[word] = max(words.get(word, 0), freq)
        phrases[word] = pinyin.split()

    OUT.mkdir(parents=True, exist_ok=True)
    with open(OUT / "chars.tsv", "w", encoding="utf-8", newline="\n") as out:
        for char in sorted(chars, key=ord):
            out.write(f"{char}\t{chars[char]}\t{jieba.get(char, 0)}\n")

    exceptions = 0
    with open(OUT / "words.tsv", "w", encoding="utf-8", newline="\n") as out:
        for word in sorted(words, key=utf16_key):
            syllables = phrases.get(word)
            if syllables is not None and syllables != default_reading(word):
                exceptions += 1
                out.write(f"{word}\t{words[word]}\t{' '.join(syllables)}\n")
            else:
                out.write(f"{word}\t{words[word]}\n")

    with open(OUT / "LICENSES.txt", "w", encoding="utf-8", newline="\n") as out:
        out.write("Rubify's pinyin dictionary is derived from the following sources.\n")
        for title, name, url in [
            ("mozillazg/pinyin-data", "chars-LICENSE", PINYIN_DATA),
            ("mozillazg/phrase-pinyin-data", "phrases-LICENSE", PHRASE_DATA),
            ("fxsjy/jieba (dict.txt)", "jieba-LICENSE", JIEBA),
        ]:
            out.write(f"\n==== {title}\n{url}\n\n{fetch(name).strip()}\n")

    print(f"chars.tsv: {len(chars)} chars")
    print(f"words.tsv: {len(words)} words, {exceptions} with explicit pinyin")


if __name__ == "__main__":
    main()
