@echo off
REM Script de déploiement Kubernetes pour Windows
REM Utilisation: deploy.bat [namespace] [environment]

setlocal enabledelayedexpansion

set NAMESPACE=%1
if "%NAMESPACE%"=="" set NAMESPACE=banking-system

set ENVIRONMENT=%2
if "%ENVIRONMENT%"=="" set ENVIRONMENT=development

echo.
echo ==========================================
echo   Deployment Banking System
echo ==========================================
echo Namespace: %NAMESPACE%
echo Environment: %ENVIRONMENT%
echo.

REM Vérifier que kubectl est installé
where kubectl >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [X] kubectl n'est pas installé
    exit /b 1
)

REM Vérifier la connexion
kubectl cluster-info >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [X] Impossible de se connecter au cluster Kubernetes
    exit /b 1
)

echo [OK] Connexion au cluster Kubernetes etablie
echo.

REM Créer le namespace
echo Creating namespace...
kubectl create namespace %NAMESPACE% --dry-run=client -o yaml | kubectl apply -f -
echo [OK] Namespace %NAMESPACE% cree/mis a jour
echo.

REM Appliquer Kustomize
echo Deploiement des ressources...
kubectl apply -k .
echo.

REM Attendre les pods
echo Attente du demarrage des pods...
kubectl wait --for=condition=Ready pod -l app.kubernetes.io/part-of=banking-system -n %NAMESPACE% --timeout=300s

REM Afficher le statut
echo.
echo ==========================================
echo   Statut du deploiement
echo ==========================================
echo.

kubectl get all -n %NAMESPACE%
echo.

echo Services disponibles:
echo.
kubectl get svc -n %NAMESPACE% -o wide
echo.

echo Stockage persistant:
echo.
kubectl get pvc -n %NAMESPACE%
echo.

echo ==========================================
echo   Deploiement termine!
echo ==========================================
echo.
echo Acces aux services en local:
echo.
echo Customer Service:
echo kubectl port-forward svc/customer-service 8081:8081 -n %NAMESPACE%
echo.
echo Eureka:
echo kubectl port-forward svc/service-registry 8761:8761 -n %NAMESPACE%
echo.
echo MinIO:
echo kubectl port-forward svc/minio-console 9001:9001 -n %NAMESPACE%
echo.
echo.
pause
