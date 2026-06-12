import re
import logging
from dataclasses import dataclass
from datetime import date

logger = logging.getLogger(__name__)

@dataclass
class OcrResult:
    nom_extrait: str | None
    prenom_extrait: str | None
    date_naissance_extraite: date | None
    numero_document: str | None
    texte_brut: str
    confiance: float  # moyenne 0.0 → 1.0


class OcrService:
    def __init__(self):
        # Le modèle est chargé UNE SEULE FOIS ici
        # gpu=False car on n'a pas de GPU en dev local
        # gpu=True en prod si ton pod K8s a un GPU
        logger.info("Chargement du modèle EasyOCR (fr, en)...")
        import easyocr
        self.reader = easyocr.Reader(['fr', 'en'], gpu=False)
        logger.info("Modèle EasyOCR chargé.")

    def extract_from_file(self, file_path: str) -> OcrResult:
        """
        Lance l'OCR sur un fichier image ou PDF.
        Retourne un OcrResult structuré.
        """
        # EasyOCR retourne une liste de tuples :
        # [ (bounding_box, texte, score_confiance), ... ]
        results = self.reader.readtext(file_path, detail=1)

        if not results:
            return OcrResult(None, None, None, None, "", 0.0)

        # Reconstitue le texte complet en une seule chaîne
        texte_brut = " ".join(text for (_, text, _) in results)
        confiance = sum(conf for (_, _, conf) in results) / len(results)

        logger.debug(f"OCR brut : {texte_brut[:200]}")

        return OcrResult(
            nom_extrait=self._extract_nom(texte_brut),
            prenom_extrait=self._extract_prenom(texte_brut),
            date_naissance_extraite=self._extract_date(texte_brut),
            numero_document=self._extract_numero_doc(texte_brut),
            texte_brut=texte_brut,
            confiance=round(confiance, 3)
        )

    # ── Extracteurs par champ ──────────────────────────────────────

    def _extract_nom(self, text: str) -> str | None:
        """
        Patterns pour CNI camerounaise et passeport ICAO.
        La CNI camerounaise affiche : NOM / NOM DE FAMILLE / SURNAME
        """
        patterns = [
            r"NOM\s*[:/]?\s*([A-ZÉÈÊËÀÂÙÛÜÔÎÏÇ]{2,}(?:\s[A-ZÉÈÊËÀÂÙÛÜÔÎÏÇ]{2,})*)",
            r"SURNAME\s*[:/]?\s*([A-Z]{2,}(?:\s[A-Z]{2,})*)",
            r"NOM\s+DE\s+FAMILLE\s*[:/]?\s*([A-ZÉÈÊË]{2,})",
            # MRZ (Machine Readable Zone) passeport : ligne commençant par P<CMR
            r"P<CMR([A-Z]+)<<",
        ]
        for p in patterns:
            m = re.search(p, text, re.IGNORECASE | re.MULTILINE)
            if m:
                return m.group(1).strip().upper()
        return None

    def _extract_prenom(self, text: str) -> str | None:
        patterns = [
            r"PR[EÉ]NOM\s*[S]?\s*[:/]?\s*([A-ZÉÈÊËÀÂ][a-zéèêëàâ]+(?:\s[A-ZÉÈÊËÀÂ][a-zéèêëàâ]+)*)",
            r"GIVEN\s*NAME\s*[:/]?\s*([A-Z][a-z]+(?:\s[A-Z][a-z]+)*)",
            r"FIRST\s*NAME\s*[:/]?\s*([A-Z][a-z]+)",
        ]
        for p in patterns:
            m = re.search(p, text, re.IGNORECASE)
            if m:
                return m.group(1).strip()
        return None

    def _extract_date(self, text: str) -> date | None:
        """
        Gère les formats courants sur documents camerounais :
        JJ/MM/AAAA  JJ-MM-AAAA  JJ.MM.AAAA  JJ MMM AAAA
        """
        mois_fr = {
            "jan": 1, "fév": 2, "fev": 2, "mar": 3, "avr": 4,
            "mai": 5, "juin": 6, "juil": 7, "aoû": 8, "aou": 8,
            "sep": 9, "oct": 10, "nov": 11, "déc": 12, "dec": 12
        }

        # Format numérique : JJ/MM/AAAA ou variantes
        m = re.search(r"\b(\d{2})[/\-\.](\d{2})[/\-\.](\d{4})\b", text)
        if m:
            try:
                return date(int(m.group(3)), int(m.group(2)), int(m.group(1)))
            except ValueError:
                pass

        # Format texte : 15 mars 1995
        m = re.search(
            r"\b(\d{1,2})\s+([a-zéèêëûüàâô]{3,4})\w*\s+(\d{4})\b",
            text, re.IGNORECASE
        )
        if m:
            mois_str = m.group(2).lower()[:4].rstrip("s")
            mois_num = mois_fr.get(mois_str)
            if mois_num:
                try:
                    return date(int(m.group(3)), mois_num, int(m.group(1)))
                except ValueError:
                    pass

        return None

    def _extract_numero_doc(self, text: str) -> str | None:
        """
        Numéro de CNI camerounaise : 9 chiffres
        Numéro de passeport : lettre(s) + chiffres, ex CM1234567
        """
        patterns = [
            r"\b(\d{9})\b",                  # CNI : 9 chiffres
            r"\b([A-Z]{1,2}\d{6,8})\b",      # Passeport
            r"N[°º]\s*[:/]?\s*([A-Z0-9]{6,12})",  # Générique
        ]
        for p in patterns:
            m = re.search(p, text)
            if m:
                return m.group(1)
        return None


# Singleton — le modèle est lourd, on ne l'instancie qu'une fois
ocr_service = OcrService()