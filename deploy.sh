#!/bin/bash
# Script de déploiement complet sur Kubernetes
# Utilisation: ./deploy.sh [namespace] [environment]

set -e

NAMESPACE=${1:-banking-system}
ENVIRONMENT=${2:-development}

echo "=========================================="
echo "🚀 Déploiement Banking System"
echo "=========================================="
echo "Namespace: $NAMESPACE"
echo "Environment: $ENVIRONMENT"
echo ""

# Couleurs
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Fonction pour imprimer avec couleur
print_status() {
    echo -e "${GREEN}[✓]${NC} $1"
}

print_error() {
    echo -e "${RED}[✗]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[!]${NC} $1"
}

# Vérifier que kubectl est installé
if ! command -v kubectl &> /dev/null; then
    print_error "kubectl n'est pas installé"
    exit 1
fi

# Vérifier la connexion à Kubernetes
if ! kubectl cluster-info &> /dev/null; then
    print_error "Impossible de se connecter au cluster Kubernetes"
    exit 1
fi

print_status "Connexion au cluster Kubernetes établie"

# Créer le namespace
echo ""
echo "📦 Création du namespace..."
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -
print_status "Namespace $NAMESPACE créé/mis à jour"

# Appliquer la configuration Kustomize
echo ""
echo "📋 Déploiement des ressources..."
kubectl apply -k .

# Attendre que les pods soient prêts
echo ""
echo "⏳ Attente que les pods démarrent..."
kubectl wait --for=condition=Ready pod -l app.kubernetes.io/part-of=banking-system -n $NAMESPACE --timeout=300s || print_warning "Certains pods ne sont pas prêts"

# Afficher le statut
echo ""
echo "📊 Statut du déploiement:"
echo ""
kubectl get all -n $NAMESPACE
echo ""

# Afficher les services
echo "🔌 Services disponibles:"
echo ""
kubectl get svc -n $NAMESPACE -o wide
echo ""

# Afficher les PVC
echo "💾 Stockage persistant:"
echo ""
kubectl get pvc -n $NAMESPACE
echo ""

# Instructions de port-forward
echo ""
echo "=========================================="
echo "✅ Déploiement terminé!"
echo "=========================================="
echo ""
echo "📡 Pour accéder aux services en local:"
echo ""
echo "# Customer Service"
echo "kubectl port-forward svc/customer-service 8081:8081 -n $NAMESPACE"
echo ""
echo "# Eureka"
echo "kubectl port-forward svc/service-registry 8761:8761 -n $NAMESPACE"
echo ""
echo "# MinIO"
echo "kubectl port-forward svc/minio-console 9001:9001 -n $NAMESPACE"
echo ""
echo "# Kafka UI"
echo "kubectl port-forward svc/kafka-ui 8080:8080 -n $NAMESPACE"
echo ""
echo "📚 Pour voir les logs:"
echo "kubectl logs -f deployment/customer-service -n $NAMESPACE"
echo ""
echo "📊 Pour voir les events:"
echo "kubectl describe node"
echo ""
