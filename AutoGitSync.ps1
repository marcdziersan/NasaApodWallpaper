param(
    # Projektverzeichnis (Root des Repos)
    [string]$RootDir = ".",

    # GitHub-User oder Organisation
    [string]$User = "marcdziersan",

    # Sichtbarkeit neuer Repos (falls git init noetig ist)
    [ValidateSet("private", "public", "internal")]
    [string]$Visibility = "private",

    # Nur anzeigen, was passieren wuerde – keine Schreiboperationen
    [switch]$DryRun
)

Write-Host "AutoGitSync - lokales Projekt mit GitHub synchronisieren" -ForegroundColor Cyan
Write-Host "RootDir    : $RootDir" -ForegroundColor Cyan
Write-Host "User       : $User" -ForegroundColor Cyan
Write-Host "Visibility : $Visibility" -ForegroundColor Cyan
Write-Host "DryRun     : $DryRun" -ForegroundColor Cyan
Write-Host ""

# 1. pruefen, ob git verfuegbar ist
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Host "Error: 'git' command not found. Please install Git or fix PATH." -ForegroundColor Red
    exit 1
}

# 2. gh nur pruefen, wenn wir ggf. ein Remote/Repo anlegen muessen
$ghAvailable = $false
if (Get-Command gh -ErrorAction SilentlyContinue) {
    $ghAvailable = $true
} else {
    Write-Host "Hinweis: 'gh' (GitHub CLI) nicht gefunden. Existierende Remotes funktionieren, neue GitHub-Repos koennen aber nicht automatisch angelegt werden." -ForegroundColor DarkYellow
}

# 3. RootDir aufloesen
try {
    $RootDir = (Resolve-Path $RootDir).Path
} catch {
    Write-Host "Error: RootDir '$RootDir' konnte nicht aufgeloest werden." -ForegroundColor Red
    exit 1
}

Write-Host "Verwende Projektordner: $RootDir" -ForegroundColor DarkCyan
Write-Host ""

# 4. Alle Dateien rekursiv auflisten (Info / Kontrolle)
Write-Host "Dateiliste (rekursiv):" -ForegroundColor Green
Get-ChildItem -Path $RootDir -Recurse -File -ErrorAction SilentlyContinue |
    ForEach-Object {
        $relPath = $_.FullName.Substring($RootDir.Length).TrimStart('\','/')
        Write-Host "  $relPath"
    }
Write-Host ""

# 5. pruefen, ob im RootDir bereits ein Git-Repo existiert
$gitFolder = Join-Path $RootDir ".git"
$hasGit = Test-Path $gitFolder

if (-not $hasGit) {
    Write-Host "Kein Git-Repository im Projektordner gefunden (.git fehlt)." -ForegroundColor Yellow

    if ($DryRun) {
        Write-Host "DryRun: wuerde folgendes tun:" -ForegroundColor DarkYellow
        Write-Host "  - git init"
        Write-Host "  - Branch auf 'main' setzen"
        Write-Host "  - alle Dateien hinzufuegen (git add .)"
        Write-Host "  - initialen Commit anlegen"
        if ($ghAvailable) {
            $repoName = Split-Path $RootDir -Leaf
            Write-Host "  - GitHub-Repo '$User/$repoName' erstellen (Visibility: $Visibility) und origin setzen"
            Write-Host "  - initialen Push auf 'main' durchfuehren"
        } else {
            Write-Host "  - Hinweis: gh nicht vorhanden, daher kein automatisches GitHub-Repo moeglich."
        }
        exit 0
    }

    Push-Location $RootDir

    # Git-Repo initialisieren
    Write-Host "Initialisiere neues Git-Repository..." -ForegroundColor Cyan
    git init | Out-Null

    # Auf main umbenennen/setzen
    git branch -M main | Out-Null

    # Alle Dateien hinzufuegen
    git add .

    # Initialen Commit anlegen
    git commit -m "Initial commit (AutoGitSync)" | Out-Null

    # GitHub-Repo anlegen und origin/push setzen, sofern gh verfuegbar ist
    $repoName = Split-Path $RootDir -Leaf

    if ($ghAvailable) {
        Write-Host "Erzeuge GitHub-Repo '$User/$repoName' und fuehre initialen Push durch..." -ForegroundColor Cyan

        $ghArgs = @(
            "repo", "create", "$User/$repoName",
            "--$Visibility",
            "--source", ".",
            "--remote", "origin",
            "--push"
        )

        & gh @ghArgs
        if ($LASTEXITCODE -eq 0) {
            Write-Host "-> Neues GitHub-Repo erstellt und initialer Push durchgefuehrt." -ForegroundColor Green
        } else {
            Write-Host "Fehler bei 'gh repo create' (ExitCode: $LASTEXITCODE)." -ForegroundColor Red
        }
    } else {
        Write-Host "gh nicht verfuegbar - bitte Remote selbst setzen, z. B.:" -ForegroundColor DarkYellow
        Write-Host "  git remote add origin https://github.com/$User/$repoName.git" -ForegroundColor DarkYellow
        Write-Host "  git push -u origin main" -ForegroundColor DarkYellow
    }

    Pop-Location
    Write-Host "Fertig. Neues Repository wurde initialisiert." -ForegroundColor Green
    exit 0
}

# 6. Es gibt bereits ein Git-Repo – Aktualisierung / Sync
Write-Host "Git-Repository erkannt (Ordner enthaelt .git)." -ForegroundColor Green

Push-Location $RootDir

# 6.1 aktuellen Branch ermitteln
$branch = (git rev-parse --abbrev-ref HEAD).Trim()
if (-not $branch -or $branch -eq "HEAD") {
    # Fallback
    $branch = "main"
}
Write-Host "Aktueller Branch: $branch" -ForegroundColor DarkCyan

# 6.2 pruefen, ob ein origin-Remote existiert
$originUrl = $null
try {
    $originUrl = git remote get-url origin 2>$null
} catch {
    # ignorieren
}

if ($originUrl) {
    Write-Host "Remote 'origin' existiert: $originUrl" -ForegroundColor DarkCyan
} else {
    Write-Host "Kein 'origin'-Remote gesetzt." -ForegroundColor Yellow

    $repoName = Split-Path $RootDir -Leaf

    if ($DryRun) {
        if ($ghAvailable) {
            Write-Host "DryRun: wuerde GitHub-Repo '$User/$repoName' erstellen und origin setzen/pushen." -ForegroundColor DarkYellow
        } else {
            Write-Host "DryRun: gh nicht verfuegbar, kein automatischer Remote/Pusherstellungs-Schritt moeglich." -ForegroundColor DarkYellow
        }
    } else {
        if ($ghAvailable) {
            Write-Host "Erzeuge GitHub-Repo '$User/$repoName' und setze origin/push..." -ForegroundColor Cyan

            $ghArgs = @(
                "repo", "create", "$User/$repoName",
                "--$Visibility",
                "--source", ".",
                "--remote", "origin",
                "--push"
            )

            & gh @ghArgs
            if ($LASTEXITCODE -eq 0) {
                Write-Host "-> GitHub-Repo erstellt und initialer Push durchgefuehrt." -ForegroundColor Green
                $originUrl = git remote get-url origin 2>$null
            } else {
                Write-Host "Fehler bei 'gh repo create' (ExitCode: $LASTEXITCODE)." -ForegroundColor Red
            }
        } else {
            Write-Host "gh nicht verfuegbar - origin muss ggf. manuell gesetzt werden." -ForegroundColor DarkYellow
        }
    }
}

# 6.3 Aenderungen pruefen (Working Tree / Index)
Write-Host "Pruefe auf Aenderungen im Repository..." -ForegroundColor Cyan
$changes = git status --porcelain

$hasLocalChanges = -not [string]::IsNullOrWhiteSpace($changes)

if ($hasLocalChanges) {
    Write-Host "Es wurden Aenderungen gefunden:" -ForegroundColor Yellow
    $changes | ForEach-Object { Write-Host "  $_" }
} else {
    Write-Host "Keine lokalen Aenderungen." -ForegroundColor Green
}

if ($DryRun) {
    Write-Host "DryRun: wuerde folgendes tun:" -ForegroundColor DarkYellow
    if ($hasLocalChanges) {
        Write-Host "  - alle Aenderungen mit 'git add .' hinzufuegen"
        Write-Host "  - Auto-Commit erstellen"
    }
    if ($originUrl) {
        Write-Host "  - 'git pull --rebase origin $branch' ausfuehren (Remote-Aenderungen holen)"
        Write-Host "  - 'git push origin $branch' ausfuehren (lokale Aenderungen hochladen)"
    } else {
        Write-Host "  - kein origin gesetzt, daher kein Pull/Push moeglich"
    }
    Pop-Location
    exit 0
}

# 6.4 Lokale Aenderungen ggf. committen
if ($hasLocalChanges) {
    Write-Host "Fuege Aenderungen hinzu (git add .)..." -ForegroundColor Cyan
    git add .

    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $commitMessage = "Auto-commit ($timestamp)"

    Write-Host "Erzeuge Commit: $commitMessage" -ForegroundColor Cyan
    git commit -m "$commitMessage"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Fehler bei 'git commit' (ExitCode: $LASTEXITCODE)." -ForegroundColor Red
        Pop-Location
        exit 1
    }
} else {
    Write-Host "Keine neuen Commits erforderlich." -ForegroundColor DarkCyan
}

# 6.5 Remote-Aenderungen holen (Pull mit Rebase), wenn origin vorhanden
if ($originUrl) {
    Write-Host "Hole Remote-Aenderungen mit 'git pull --rebase origin $branch'..." -ForegroundColor Cyan
    git pull --rebase origin $branch
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Fehler bei 'git pull --rebase origin $branch' (ExitCode: $LASTEXITCODE)." -ForegroundColor Red
        Write-Host "Hinweis: Es sind vermutlich Merge-Konflikte aufgetreten." -ForegroundColor DarkYellow
        Write-Host "Bitte im Projektordner manuell loesen:" -ForegroundColor DarkYellow
        Write-Host "  git status" -ForegroundColor DarkYellow
        Write-Host "  Konflikte in Dateien bearbeiten, dann:" -ForegroundColor DarkYellow
        Write-Host "  git add <Dateien>" -ForegroundColor DarkYellow
        Write-Host "  git rebase --continue" -ForegroundColor DarkYellow
        Write-Host "Anschliessend erneut AutoGitSync ausfuehren." -ForegroundColor DarkYellow
        Pop-Location
        exit 1
    }
} else {
    Write-Host "Kein origin gesetzt - ueberspringe Pull und Push." -ForegroundColor DarkYellow
    Pop-Location
    exit 0
}

# 6.6 Lokale Aenderungen nach origin pushen
Write-Host "Pushe Aenderungen nach 'origin $branch'..." -ForegroundColor Cyan
git push origin $branch

if ($LASTEXITCODE -eq 0) {
    Write-Host "-> Aenderungen erfolgreich gepusht." -ForegroundColor Green
} else {
    Write-Host "Fehler bei 'git push origin $branch' (ExitCode: $LASTEXITCODE)." -ForegroundColor Red
    Write-Host "Hinweis: wenn dieser Fehler erneut auftritt, bitte manuell pruefen:" -ForegroundColor DarkYellow
    Write-Host "  git status" -ForegroundColor DarkYellow
    Write-Host "  git log --oneline --graph --all" -ForegroundColor DarkYellow
}

Pop-Location
Write-Host "AutoGitSync abgeschlossen." -ForegroundColor Green
