from difflib import SequenceMatcher
from datetime import date
import logging

logger = logging.getLogger(__name__)


class CoherenceService:
    """
    Compare les données extraites par OCR avec les données saisies
    par le client lors de l'inscription.
    Retourne (coherent: bool, commentaire: str).
    """

    # Seuils de similarité (ajustables selon la qualité des scans)
    SEUIL_NOM = 0.75      # 75% de similarité minimum pour le nom
    SEUIL_PRENOM = 0.70   # un peu plus souple (prénoms composés, accents)

    def verifier(
        self,
        ocr_nom: str | None,
        ocr_prenom: str | None,
        ocr_date: date | None,
        client_nom: str,
        client_prenom: str,
        client_date: date
    ) -> tuple[bool, str]:

        erreurs = []

        # ── Vérification du nom ────────────────────────────────────
        if ocr_nom:
            ratio = self._similarite(ocr_nom, client_nom)
            logger.debug(f"Similarité nom : '{ocr_nom}' vs '{client_nom}' = {ratio:.2f}")
            if ratio < self.SEUIL_NOM:
                erreurs.append(
                    f"Nom OCR '{ocr_nom}' ≠ nom saisi '{client_nom}' "
                    f"(similarité: {ratio:.0%})"
                )
        else:
            # OCR n'a pas trouvé de nom — on le note mais pas bloquant seul
            logger.warning("OCR : nom non extrait du document")

        # ── Vérification du prénom ─────────────────────────────────
        if ocr_prenom:
            ratio = self._similarite(ocr_prenom, client_prenom)
            logger.debug(f"Similarité prénom : '{ocr_prenom}' vs '{client_prenom}' = {ratio:.2f}")
            if ratio < self.SEUIL_PRENOM:
                erreurs.append(
                    f"Prénom OCR '{ocr_prenom}' ≠ prénom saisi '{client_prenom}' "
                    f"(similarité: {ratio:.0%})"
                )
        else:
            logger.warning("OCR : prénom non extrait du document")

        # ── Vérification de la date de naissance ──────────────────
        if ocr_date:
            if ocr_date != client_date:
                erreurs.append(
                    f"Date naissance OCR {ocr_date.isoformat()} "
                    f"≠ date saisie {client_date.isoformat()}"
                )
        else:
            logger.warning("OCR : date de naissance non extraite")

        # ── Résultat final ─────────────────────────────────────────
        if erreurs:
            commentaire = " | ".join(erreurs)
            logger.info(f"Dossier INCOHERENT : {commentaire}")
            return False, commentaire

        logger.info("Dossier COHERENT : toutes les données correspondent")
        return True, "Données cohérentes avec les informations saisies"

    def _similarite(self, a: str, b: str) -> float:
        """Similarité insensible à la casse et aux espaces superflus."""
        a_clean = a.upper().strip()
        b_clean = b.upper().strip()
        return SequenceMatcher(None, a_clean, b_clean).ratio()


coherence_service = CoherenceService()