from difflib import SequenceMatcher
import logging

logger = logging.getLogger(__name__)


class CoherenceService:
    """
    Compare les données extraites par OCR avec les données saisies
    par le client lors de l'inscription.
    Retourne (coherent: bool, commentaire: str).
    """

    # Seuils de similarité (ajustables selon la qualité des scans)
    SEUIL_NOM = 0.75    # 75% de similarité minimum pour le nom
    SEUIL_PRENOM = 0.70 # un peu plus souple (prénoms composés, accents)

    def verifier(
        self,
        ocr_nom: str | None,
        ocr_prenom: str | None,
        client_nom: str,
        client_prenom: str,
    ) -> tuple[bool, str]:

        erreurs = []

        # ── Vérification du nom ────────────────────────────────────────────────
        if ocr_nom:
            ratio = self._similarite(ocr_nom, client_nom)
            if ratio < self.SEUIL_NOM:
                erreurs.append(
                    f"Nom OCR '{ocr_nom}' ≠ nom saisi '{client_nom}' "
                    f"(similarité: {ratio:.0%})"
                )
        else:
            erreurs.append("Nom non extrait du document — vérification impossible")

        # ── Vérification du prénom ─────────────────────────────────────────────
        if ocr_prenom:
            ratio = self._similarite_prenom(ocr_prenom, client_prenom)
            if ratio < self.SEUIL_PRENOM:
                erreurs.append(
                    f"Prénom OCR '{ocr_prenom}' ≠ prénom saisi '{client_prenom}' "
                    f"(similarité: {ratio:.0%})"
                )
        else:
            erreurs.append("Prénom non extrait du document — vérification impossible")

        # ── Résultat final ─────────────────────────────────────────────────────
        if erreurs:
            commentaire = " | ".join(erreurs)
            logger.info(f"Dossier INCOHERENT : {commentaire}")
            return False, commentaire

        logger.info("Dossier COHERENT : toutes les données correspondent")
        return True, "Données cohérentes avec les informations saisies"

    # ── Helpers ───────────────────────────────────────────────────────────────

    def _similarite(self, a: str, b: str) -> float:
        """Similarité insensible à la casse et aux espaces superflus."""
        return SequenceMatcher(None, a.upper().strip(), b.upper().strip()).ratio()

    def _similarite_prenom(self, ocr: str, saisi: str) -> float:
        """Similarité pour les prénoms avec deux niveaux de tolérance :
        1. Ratio classique (comme pour le nom)
        2. Inclusion partielle : si l'OCR n'a capturé qu'un prénom sur plusieurs
           (ex: OCR='HANDY' vs saisi='HANDY ROCHINEL'), on vérifie si chaque mot
           OCR se retrouve dans le prénom saisi avec un bon ratio individuel.
           Evite les rejets injustes sur les prénoms composés longs."""
        ocr_clean = ocr.upper().strip()
        saisi_clean = saisi.upper().strip()

        ratio_global = SequenceMatcher(None, ocr_clean, saisi_clean).ratio()
        if ratio_global >= self.SEUIL_PRENOM:
            return ratio_global

        # Vérifie si tous les mots OCR matchent un mot du prénom saisi
        mots_ocr = ocr_clean.split()
        mots_saisi = saisi_clean.split()
        if mots_ocr and all(
            any(
                SequenceMatcher(None, mot_ocr, mot_saisi).ratio() >= self.SEUIL_PRENOM
                for mot_saisi in mots_saisi
            )
            for mot_ocr in mots_ocr
        ):
            return self.SEUIL_PRENOM  # on retourne exactement le seuil -> passe

        return ratio_global


coherence_service = CoherenceService()