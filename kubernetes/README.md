# Configuration Kubernetes - Banking System

## 📋 Vue d'ensemble

Cette documentation explique comment configurer et déployer le système bancaire sur Kubernetes. C'est une configuration complète avec tous les microservices, les bases de données, et les services de support.

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    KUBERNETES CLUSTER                       │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │            NAMESPACE: banking-system                 │   │
│  │                                                       │   │
│  │  ┌─────────────────────────────────────────────┐    │   │
│  │  │       MICROSERVICES (2-5 replicas)         │    │   │
│  │  │  • customer-service:8081                   │    │   │
│  │  │  • service-account:8082                    │    │   │
│  │  │  • service-transaction:8083                │    │   │
│  │  │  • service-loan:8084                       │    │   │
│  │  │  • service-document:8082                   │    │   │
│  │  │  • service-notification:8085               │    │   │
│  │  └─────────────────────────────────────────────┘    │   │
│  │                        ↑                             │   │
│  │  ┌─────────────────────────────────────────────┐    │   │
│  │  │      INFRASTRUCTURE & STORAGE               │    │   │
│  │  │  • PostgreSQL (3 instances)                │    │   │
│  │  │  • MongoDB                                  │    │   │
│  │  │  • Kafka + Zookeeper                       │    │   │
│  │  │  • MinIO (S3 storage)                      │    │   │
│  │  │  • Eureka (Service Discovery)              │    │   │
│  │  └─────────────────────────────────────────────┘    │   │
│  │                                                       │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

## 🚀 Démarrage rapide

### Prérequis

- **Kubernetes 1.20+** (Minikube, Docker Desktop K8s, ou cluster cloud)
- **kubectl** CLI configuré
- **Kustomize** (optionnel, pour la gestion avancée)

### Installation sur Windows

#### Option 1: Docker Desktop (Recommandé)

1. Installer [Docker Desktop](https://www.docker.com/products/docker-desktop)
2. Activer Kubernetes dans les paramètres
3. Vérifier l'installation:
```powershell
kubectl cluster-info
kubectl get nodes
```

#### Option 2: Minikube

```powershell
# Installer Minikube avec Chocolatey
choco install minikube

# Démarrer Minikube
minikube start --cpus=4 --memory=8192 --disk-size=50g

# Vérifier le statut
minikube status

# Ouvrir le dashboard
minikube dashboard
```

## 📦 Structure des fichiers

```
kubernetes/
├── namespaces/
│   └── namespace.yaml              # Namespace banking-system
├── infrastructure/
│   ├── eureka-server.yaml          # Service Registry
│   ├── postgres-customer.yaml      # DB pour customer
│   ├── postgres-other-services.yaml# DB pour autres services
│   ├── kafka-zookeeper.yaml        # Message Queue
│   ├── minio.yaml                  # Stockage objet
│   └── ingress-network.yaml        # Ingress et NetworkPolicy
└── services/
    ├── customer-service.yaml       # Service Client
    ├── service-account.yaml        # Service Compte
    ├── service-transaction.yaml    # Service Transaction
    ├── service-loan.yaml           # Service Prêt
    ├── service-document.yaml       # Service Document/OCR
    └── service-notification.yaml   # Service Notification

kustomization.yaml                 # Configuration Kustomize (déploiement global)
```

## 🔧 Déploiement

### 1️⃣ Déployer avec kubectl (Méthode 1 - Simple)

```powershell
# Créer le namespace
kubectl apply -f kubernetes/namespaces/namespace.yaml

# Déployer l'infrastructure
kubectl apply -f kubernetes/infrastructure/

# Déployer les microservices
kubectl apply -f kubernetes/services/
```

### 2️⃣ Déployer avec Kustomize (Méthode 2 - Recommandée)

```powershell
# Déployer tout d'un coup
kubectl apply -k kubernetes/

# Vérifier le statut
kubectl get all -n banking-system

# Voir les détails
kubectl describe pods -n banking-system
```

### 3️⃣ Déployer avec Helm (Méthode 3 - Avancée)

```powershell
# Créer un chart Helm (optionnel)
helm create banking-system

# Installer/Mettre à jour
helm install banking ./banking-system -n banking-system --create-namespace
```

## 📊 Vérification du déploiement

```powershell
# Voir les pods
kubectl get pods -n banking-system -w

# Voir les services
kubectl get svc -n banking-system

# Voir les persistentvolumeclaims
kubectl get pvc -n banking-system

# Voir les configmaps
kubectl get configmaps -n banking-system

# Voir les secrets
kubectl get secrets -n banking-system

# Détails complets d'un pod
kubectl describe pod customer-service-xxxxx -n banking-system

# Logs d'un service
kubectl logs deployment/customer-service -n banking-system -f

# Logs d'un pod spécifique
kubectl logs <pod-name> -n banking-system
```

## 🌐 Accès aux services

### Port Forwarding (Développement)

```powershell
# Customer Service
kubectl port-forward svc/customer-service 8081:8081 -n banking-system

# Account Service
kubectl port-forward svc/service-account 8082:8082 -n banking-system

# Transaction Service
kubectl port-forward svc/service-transaction 8083:8083 -n banking-system

# Loan Service
kubectl port-forward svc/service-loan 8084:8084 -n banking-system

# Document Service
kubectl port-forward svc/service-document 8082:8082 -n banking-system

# Notification Service
kubectl port-forward svc/service-notification 8085:8085 -n banking-system

# Eureka
kubectl port-forward svc/service-registry 8761:8761 -n banking-system

# MinIO Console
kubectl port-forward svc/minio-console 9001:9001 -n banking-system

# Kafka UI
kubectl port-forward svc/kafka-ui 8080:8080 -n banking-system
```

### Avec Ingress (Production)

Après avoir installé un contrôleur Ingress (NGINX, Traefik):

```powershell
# Installer NGINX Ingress Controller
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.1/deploy/static/provider/cloud/deploy.yaml

# Attendre que le LoadBalancer soit prêt
kubectl get svc -n ingress-nginx

# Accès via Ingress
# http://api.banking.local/customers
# http://api.banking.local/accounts
# http://api.banking.local/transactions
```

## 🔒 Gestion des secrets

### Créer un Secret

```powershell
# Créer un secret à partir de fichiers
kubectl create secret generic db-secret \
  --from-literal=username=admin \
  --from-literal=password=secretpass \
  -n banking-system

# Voir les secrets
kubectl get secrets -n banking-system

# Afficher un secret (décodé)
kubectl get secret postgres-customer-secret -o jsonpath='{.data.POSTGRES_PASSWORD}' -n banking-system | base64 --decode
```

### Modifier les ConfigMaps et Secrets

```powershell
# Éditer un ConfigMap
kubectl edit configmap customer-service-config -n banking-system

# Éditer un Secret
kubectl edit secret postgres-customer-secret -n banking-system

# Appliquer les changements
kubectl rollout restart deployment/customer-service -n banking-system
```

## 📈 Monitoring et Logs

### Prometheus et Grafana

```powershell
# Installer Prometheus (optionnel)
kubectl apply -f https://github.com/prometheus-community/kube-prometheus-stack/releases/download/kube-prometheus-stack-54.0.0/kube-prometheus-stack-54.0.0.tgz

# Accès Grafana
kubectl port-forward -n monitoring svc/kube-prometheus-stack-grafana 3000:80
# http://localhost:3000 (admin/prom-operator)
```

### Kibana/ELK Stack

```powershell
# Voir les logs en temps réel
kubectl logs -f deployment/customer-service -n banking-system

# Logs des 100 dernières lignes
kubectl logs --tail=100 deployment/customer-service -n banking-system

# Logs d'une période spécifique
kubectl logs --since=1h deployment/customer-service -n banking-system
```

## 🔄 Mise à jour des services

### Rolling Update

```powershell
# Mettre à jour l'image
kubectl set image deployment/customer-service \
  customer-service=customer-service:2.0.0 \
  -n banking-system

# Voir le statut du rolling update
kubectl rollout status deployment/customer-service -n banking-system

# Annuler en cas de problème
kubectl rollout undo deployment/customer-service -n banking-system

# Voir l'historique
kubectl rollout history deployment/customer-service -n banking-system
```

## 🗑️ Nettoyage

```powershell
# Supprimer tous les ressources du namespace
kubectl delete namespace banking-system

# Supprimer une déploiement spécifique
kubectl delete deployment customer-service -n banking-system

# Supprimer un service
kubectl delete svc customer-service -n banking-system

# Supprimer les PVC (attention: données perdues!)
kubectl delete pvc --all -n banking-system
```

## 📋 Checklist de déploiement

- [ ] Kubernetes installé et configuré
- [ ] Docker images construites pour tous les services
- [ ] Images disponibles dans un registre accessible
- [ ] namespace créé
- [ ] Infrastructure déployée (BD, Kafka, MinIO)
- [ ] Services registry (Eureka) en ligne
- [ ] Microservices déployés avec au moins 2 replicas
- [ ] Tous les pods en état "Running"
- [ ] Services accessibles via port-forward
- [ ] Logs vérifiés - aucune erreur critique
- [ ] Santé vérifiée via /actuator/health
- [ ] Configuration de l'Ingress (optionnel)

## ⚙️ Configuration pour la production

### Ressources

```yaml
resources:
  requests:
    memory: "1Gi"      # Minimum garanti
    cpu: "1000m"       # 1 CPU
  limits:
    memory: "2Gi"      # Maximum autorisé
    cpu: "2000m"       # 2 CPU
```

### Réplicas

```yaml
replicas: 3  # Au minimum 3 pour production
```

### HorizontalPodAutoscaler

```yaml
minReplicas: 2
maxReplicas: 10
metrics:
  - cpu: 70%
  - memory: 80%
```

### PersistentVolumes

- Utiliser un type de stockage durable (SSD, EBS, GCE Persistent Disk)
- Implémenter des backups réguliers
- Tester la restauration

## 🐛 Dépannage

### Les pods ne démarrent pas

```powershell
# Voir les événements
kubectl describe pod <pod-name> -n banking-system

# Voir les logs
kubectl logs <pod-name> -n banking-system

# Vérifier les ressources
kubectl top nodes
kubectl top pods -n banking-system
```

### Les services ne se communiquent pas

```powershell
# Tester la connectivité
kubectl exec -it <pod-name> -n banking-system -- ping <service-name>

# Vérifier les DNS
kubectl exec -it <pod-name> -n banking-system -- nslookup <service-name>

# Tester HTTP
kubectl exec -it <pod-name> -n banking-system -- curl http://<service-name>:8081
```

### Problèmes de stockage

```powershell
# Voir les PVC
kubectl get pvc -n banking-system

# Voir les PV
kubectl get pv

# Voir les StorageClasses
kubectl get storageclasses
```

## 📚 Ressources supplémentaires

- [Documentation Kubernetes officielle](https://kubernetes.io/docs/)
- [Kustomize Documentation](https://kustomize.io/)
- [Helm Charts](https://helm.sh/)
- [CNCF Security Best Practices](https://www.cncf.io/blog/2021/12/15/kubernetes-security-best-practices/)

## 📞 Support

Pour toute question ou problème:
1. Vérifier les logs: `kubectl logs`
2. Vérifier les événements: `kubectl describe`
3. Consulter la documentation Kubernetes
4. Ouvrir un ticket auprès de l'équipe DevOps
