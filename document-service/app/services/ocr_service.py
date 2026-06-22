import re
import logging
import difflib
import unicodedata
from dataclasses import dataclass
from datetime import date

import pytesseract
from PIL import Image, ImageOps

logger = logging.getLogger(__name__)

pytesseract.pytesseract.tesseract_cmd = r'C:\Program Files\Tesseract-OCR\tesseract.exe'


@dataclass
class OcrResult:
    nom_extrait: str | None
    prenom_extrait: str | None
    date_naissance_extraite: date | None
    numero_document: str | None
    texte_brut: str
    confiance: float


# Patterns de labels FR/EN courts (stems) -> tolèrent les troncatures OCR.
# On utilise des stems plutôt que les mots complets car le texte vert/clair
# de la carte est souvent tronqué ou déformé par Tesseract.
_LABEL_PATTERNS = {
    "nom":       (r"\bNOM\b",    r"SURNAME"),
    "prenom":    (r"PRENOM",     r"GIVEN"),
    "naissance": (r"NAISSA",     r"BIRTH"),
    "lieu":      (r"\bLIEU\b",   r"PLACE"),
    "sexe":      (r"\bSEXE\b",   r"\bSEX\b"),
    "taille":    (r"TAILLE",     r"HEIGHT"),
    "profession":(r"PROFESS",    r"OCCUPATION"),
    "expiration":(r"EXPIR",      r"EXPIRY"),
}

# Mots-labels complets pour le filtrage fuzzy : on refuse tout token OCR qui
# ressemble à l'un d'eux (même déformé, ex: "REPUBLIQLU" ~ "REPUBLIQUE").
_LABEL_STEMS = [
    "REPUBLIQUE", "CAMEROUN", "CAMEROON", "REPUBLIC", "NATIONALE", "NATIONAL",
    "IDENTITE", "IDENTITY", "CARTE", "PASSEPORT", "PASSPORT",
    "NOM", "SURNAME", "PRENOM", "PRENOMS", "GIVEN", "NAMES",
    "DATE", "NAISSANCE", "BIRTH", "LIEU", "PLACE",
    "SEXE", "SEX", "TAILLE", "HEIGHT",
    "PROFESSION", "OCCUPATION", "SIGNATURE",
    "EXPIRATION", "EXPIRY", "VALABLE", "JUSQU", "HOLDER",
    "DELIVREE", "NATIONALITE", "NATIONALITY", "AUTORITE",
]

# Connecteurs courts toujours du bruit (trop courts pour le fuzzy)
_STOPWORDS = {"DE", "DU", "OF", "LA", "LE", "ET", "AND", "AU"}

_MOIS_FR = {
    "jan": 1, "fev": 2, "fév": 2, "mar": 3, "avr": 4,
    "mai": 5, "juin": 6, "juil": 7, "aou": 8, "aoû": 8,
    "sep": 9, "oct": 10, "nov": 11, "dec": 12, "déc": 12,
}


class OcrService:

    # ── Public ────────────────────────────────────────────────────────────────

    def extract_from_file(self, file_path: str) -> OcrResult:
        image = self._preprocess(Image.open(file_path))
        texte_brut = pytesseract.image_to_string(image, lang="fra+eng", config="--psm 6")
        logger.debug(f"OCR brut :\n{texte_brut}")

        lines = self._split_lines(texte_brut)

        nom    = self._extract_nom(texte_brut, lines)
        prenom = self._extract_prenom(texte_brut, lines)
        logger.info(f"OCR extrait → nom={nom!r}, prénom={prenom!r}")

        dob    = self._extract_date(texte_brut)
        num    = self._extract_numero_doc(texte_brut)

        # Confiance : proportion des 4 champs clés trouvés
        found = sum(bool(v) for v in [nom, prenom, dob, num])
        confiance = round(found / 4, 2)

        return OcrResult(
            nom_extrait=nom,
            prenom_extrait=prenom,
            date_naissance_extraite=dob,
            numero_document=num,
            texte_brut=texte_brut,
            confiance=confiance,
        )

    # ── Preprocessing ─────────────────────────────────────────────────────────

    def _preprocess(self, image: Image.Image) -> Image.Image:
        """Grayscale + autocontrast + upscale si petite image.
        Les labels verts/clairs sur la CNI ont un faible contraste :
        cette étape améliore significativement leur lisibilité par Tesseract."""
        gray = ImageOps.grayscale(image)
        gray = ImageOps.autocontrast(gray, cutoff=2)
        if gray.width < 1500:
            ratio = 1500 / gray.width
            gray = gray.resize(
                (int(gray.width * ratio), int(gray.height * ratio)),
                Image.LANCZOS,
            )
        return gray

    # ── Extraction NOM ────────────────────────────────────────────────────────

    def _extract_nom(self, text: str, lines: list[str]) -> str | None:
        # Stratégie 1 : label détecté -> valeur nettoyée sur même ligne ou suivante
        result = self._extract_by_label(lines, _LABEL_PATTERNS["nom"])
        if result:
            return result

        # Stratégie 2 fallback : 1ère ligne all-caps valide avant la date
        candidates = self._guess_name_lines(lines)
        return candidates[0] if candidates else None

    # ── Extraction PRÉNOM ─────────────────────────────────────────────────────

    def _extract_prenom(self, text: str, lines: list[str]) -> str | None:
        # Stratégie 1 : label détecté
        result = self._extract_by_label(lines, _LABEL_PATTERNS["prenom"])
        if result:
            return result

        # Stratégie 2 fallback : 2ème ligne all-caps valide (après le nom)
        candidates = self._guess_name_lines(lines)
        return candidates[1] if len(candidates) >= 2 else None

    # ── Extraction DATE ───────────────────────────────────────────────────────

    def _extract_date(self, text: str) -> date | None:
        # DD.MM.YYYY / DD/MM/YYYY / DD-MM-YYYY
        m = re.search(r"\b(\d{2})[/\-\.](\d{2})[/\-\.](\d{4})\b", text)
        if m:
            try:
                return date(int(m.group(3)), int(m.group(2)), int(m.group(1)))
            except ValueError:
                pass

        # "3 septembre 2002" / "03 SEP 2002"
        m = re.search(
            r"\b(\d{1,2})\s+([a-zéèêëûüàâô]{3,})\s+(\d{4})\b",
            text, re.IGNORECASE,
        )
        if m:
            mois_num = _MOIS_FR.get(m.group(2).lower()[:4].rstrip("s"))
            if mois_num:
                try:
                    return date(int(m.group(3)), mois_num, int(m.group(1)))
                except ValueError:
                    pass
        return None

    # ── Extraction NUMÉRO DOCUMENT ────────────────────────────────────────────

    def _extract_numero_doc(self, text: str) -> str | None:
        patterns = [
            r"\b([A-Z]{1,2}\d{6,8})\b",
            r"N[°º]\s*[:/]?\s*([A-Z0-9]{6,12})",
            r"\b(\d{9})\b",
        ]
        for p in patterns:
            m = re.search(p, text)
            if m:
                return m.group(1)
        return None

    # ── Helpers partagés ──────────────────────────────────────────────────────

    def _split_lines(self, text: str) -> list[str]:
        """Découpe en lignes, strip + normalise accents, vire les vides."""
        lines = []
        for raw in text.splitlines():
            stripped = self._strip_accents(raw).strip()
            if stripped:
                lines.append(stripped)
        return lines

    def _extract_by_label(self, lines: list[str], label_patterns: tuple[str, str]) -> str:
        """Cherche un label (FR ou EN), retourne la valeur nettoyée sur la même
        ligne ou l'une des 2 lignes suivantes. Tolère les labels tronqués/déformés
        car on utilise des stems courts (pas le mot complet)."""
        fr_pattern, en_pattern = label_patterns
        combined = re.compile(rf"({fr_pattern}|{en_pattern})", re.IGNORECASE)
        for idx, line in enumerate(lines):
            if not combined.search(line):
                continue
            # Valeur sur la même ligne (après le label)
            same = self._clean_candidate(line)
            if same:
                return same
            # Valeur sur les 2 lignes suivantes
            for offset in range(1, 3):
                if idx + offset >= len(lines):
                    break
                candidate = self._clean_candidate(lines[idx + offset])
                if candidate and not self._is_label_line(lines[idx + offset]):
                    return candidate
        return ""

    def _extract_sex(self, lines: list[str]) -> str:
        """M/F isolé : cas particulier, une seule lettre -> le nettoyage générique
        par tokens majuscules serait trop permissif ici."""
        fr_pattern, en_pattern = _LABEL_PATTERNS["sexe"]
        combined = re.compile(rf"({fr_pattern}|{en_pattern})", re.IGNORECASE)
        for idx, line in enumerate(lines):
            if not combined.search(line):
                continue
            for offset in range(0, 3):
                if idx + offset >= len(lines):
                    break
                m = re.search(r"\b([MF])\b", lines[idx + offset])
                if m:
                    return m.group(1)
        return ""

    def _looks_like_label_word(self, token: str) -> bool:
        """True si le token ressemble (même déformé) à un mot-label connu.
        Combine préfixe/suffixe exact + ratio difflib >= 0.72 pour couvrir
        les déformations OCR sans piquer de vrais noms courts."""
        for stem in _LABEL_STEMS:
            if token.startswith(stem) or stem.startswith(token):
                return True
            if difflib.SequenceMatcher(None, token, stem).ratio() >= 0.72:
                return True
        return False

    def _clean_candidate(self, line: str) -> str:
        """Garde uniquement les tokens tout-en-majuscules (≥2 lettres).
        Filtre stopwords courts + tout token ressemblant à un label (fuzzy).
        Le bruit OCR est quasi toujours en minuscules/mixte -> ce filtrage
        isole les vraies valeurs imprimées en capitales sur la carte."""
        tokens = re.findall(r"[A-Z]{2,}(?:['\-][A-Z]+)*", line)
        tokens = [
            t for t in tokens
            if t not in _STOPWORDS and not self._looks_like_label_word(t)
        ]
        return " ".join(tokens)

    def _is_label_line(self, line: str) -> bool:
        """True si la ligne contient un pattern de label connu."""
        for fr_pattern, en_pattern in _LABEL_PATTERNS.values():
            if re.search(fr_pattern, line, re.IGNORECASE) or re.search(en_pattern, line, re.IGNORECASE):
                return True
        return False

    def _guess_name_lines(self, lines: list[str]) -> list[str]:
        """Fallback quand les labels NOM/PRENOMS ont disparu du rawText OCR :
        on collecte les lignes nettoyées (all-caps, sans bruit, sans labels)
        qui apparaissent avant la première date de naissance trouvée."""
        candidates = []
        for line in lines:
            if re.search(r"\d{2}[./]\d{2}[./]\d{4}", line):
                if candidates:
                    break
                continue
            if self._is_label_line(line):
                continue
            cleaned = self._clean_candidate(line)
            if cleaned and len(cleaned) > 1:
                candidates.append(cleaned)
        return candidates

    def _strip_accents(self, value: str) -> str:
        return "".join(
            char for char in unicodedata.normalize("NFKD", value)
            if not unicodedata.combining(char)
        )


ocr_service = OcrService()