package fortlev;

import robocode.*;
import static robocode.util.Utils.normalRelativeAngleDegrees;
import java.awt.*;

/**
 * AndersonSilva - a robot by (Arthur Abdala, Arthur de Oliveira, Mateus Raffaelli e Matheus Posada)
 */
public class AndersonSilva extends AdvancedRobot {

    // ===== VARIÁVEIS DE CONTROLE =====
    double velocidadeRadar = 30;
    boolean girandoDireita = true;
    double passo = 1.0;
    double incremento = 0.08;
    double angTurn = 3.0;
    boolean expandindo = true;
    int count = 0;
    double gunTurnAmt;
    String trackName;
    boolean wantToFire = false;
    double lastFirePower = 3;
	
// ✅ NOVAS VARIÁVEIS PARA PREDIÇÃO
    double enemyVelocity = 0;        // velocidade do inimigo
    double enemyHeading = 0;         // direção do inimigo
    double enemyDistance = 0;        // distância do inimigo
    double enemyBearing = 0;         // ângulo relativo ao inimigo


    // ===== MÉTODO PRINCIPAL =====
    public void run() {
        // === CONFIGURAÇÃO DE CORES ===
        setBodyColor(Color.black);
        setGunColor(Color.white);
        setRadarColor(Color.white);
        setScanColor(Color.red);

        // Permite que radar e canhão girem de forma independente do corpo
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        
        trackName = null;
        gunTurnAmt = 10;

        // === LOOP PRINCIPAL ===
        while (true) {
            // === EVITAR PAREDES (CHECAR PRIMEIRO!) ===
            double margemSeguranca = 80;
            boolean pertoParede = false;
            
            if (getX() < margemSeguranca || 
                getX() > getBattleFieldWidth() - margemSeguranca ||
                getY() < margemSeguranca || 
                getY() > getBattleFieldHeight() - margemSeguranca) {
                
                pertoParede = true;
                
                // Calcula direção para o centro do campo
                double anguloParaCentro = Math.toDegrees(Math.atan2(
                    getBattleFieldWidth()/2 - getX(),
                    getBattleFieldHeight()/2 - getY()
                ));
                
                double virar = normalRelativeAngleDegrees(anguloParaCentro - getHeading());
                
                setTurnRight(virar);
                setAhead(150);
                out.println("EVITANDO PAREDE! Indo para o centro...");
            }
            
            // ✅ MOVIMENTO ESPIRAL (só se NÃO estiver perto da parede)
            if (!pertoParede) {
                setTurnRight(angTurn);
                setAhead(passo);
                
                // Ajustar passo para gerar espiral
                if (expandindo) {
                    passo += incremento;
                    if (passo > 150) expandindo = false;
                } else {
                    passo -= incremento;
                    if (passo < 10) {
                        expandindo = true;
                        setTurnRight(45);
                        setAhead(100);
                    }
                }
            }

            // ✅ CORREÇÃO 2: VARREDURA DO RADAR SIMPLIFICADA
            // Se NÃO temos alvo, varre em círculos
            if (trackName == null) {
                setTurnRadarRight(360);
                setTurnGunRight(gunTurnAmt);
            }
            // Se JÁ temos alvo, o radar será ajustado no onScannedRobot

            // ✅ CORREÇÃO 3: ATIRAR COM CONDIÇÕES MAIS FLEXÍVEIS
            double gunRemaining = Math.abs(getGunTurnRemaining());
            double GUN_ANGLE_TOLERANCE = 10.0;  // Aumentado para 10 graus (mais tolerante)
            
            // Debug: mostra status do disparo
            if (trackName != null && wantToFire) {
                out.println("Tentando atirar - gunRemaining: " + gunRemaining + 
                           ", gunHeat: " + getGunHeat());
            }
            
            if (trackName != null && wantToFire && 
                gunRemaining <= GUN_ANGLE_TOLERANCE && 
                getGunHeat() == 0) {  // ✅ Verifica se canhão está frio
                
                out.println("DISPARANDO com potência " + lastFirePower);
                fire(lastFirePower);
                wantToFire = false;
            }

            // Lógica de "procura" de alvo usando count
            count++;

            if (count > 2) gunTurnAmt = -10;
            if (count > 5) gunTurnAmt = 10;
            if (count > 11) trackName = null;

            execute(); // Executa todos os comandos pendentes
        }
    }
	
	// ===== MÉTODO DE PREDIÇÃO DE TIRO =====
    /**
     * Calcula o ângulo para atirar prevendo onde o inimigo estará
     * Usa predição linear simples (assume que o inimigo continuará em linha reta)
     */
    public double calcularAnguloPreditivo() {
        // Velocidade da bala baseada na potência do tiro
        double bulletSpeed = 20 - 3 * lastFirePower;
        
        // Tempo que a bala levará para chegar ao inimigo
        double tempoParaImpacto = enemyDistance / bulletSpeed;
        
        // Calcula quanto o inimigo se moverá nesse tempo
        double deslocamentoInimigo = enemyVelocity * tempoParaImpacto;
        
        // Posição atual do inimigo em coordenadas absolutas
        double anguloAbsolutoInimigo = getHeading() + enemyBearing;
        double enemyX = getX() + Math.sin(Math.toRadians(anguloAbsolutoInimigo)) * enemyDistance;
        double enemyY = getY() + Math.cos(Math.toRadians(anguloAbsolutoInimigo)) * enemyDistance;
        
        // Posição futura do inimigo (predição linear)
        double futuroX = enemyX + Math.sin(Math.toRadians(enemyHeading)) * deslocamentoInimigo;
        double futuroY = enemyY + Math.cos(Math.toRadians(enemyHeading)) * deslocamentoInimigo;
        
        // Calcula o ângulo para a posição futura
        double anguloParaFuturo = Math.toDegrees(Math.atan2(
            futuroX - getX(), 
            futuroY - getY()
        ));
        
        // Retorna o quanto precisa girar o canhão
        return normalRelativeAngleDegrees(anguloParaFuturo - getGunHeading());
    }


    // ===== EVENTO: QUANDO DETECTA UM INIMIGO =====
    public void onScannedRobot(ScannedRobotEvent e) {
        
        // Se já estamos rastreando outro robô, ignora este
        if (trackName != null && !e.getName().equals(trackName)) {
            return;
        }

        // Define o alvo
        if (trackName == null) {
            trackName = e.getName();
            out.println("Tracking " + trackName);
        }
        
        // Reseta o contador (alvo encontrado)
        count = 0;
		
		// ✅ ARMAZENA DADOS DO INIMIGO PARA PREDIÇÃO
        enemyVelocity = e.getVelocity();
        enemyHeading = e.getHeading();
        enemyDistance = e.getDistance();
        enemyBearing = e.getBearing();

        // ✅ CORREÇÃO 4: RADAR LOCK-ON (trava no alvo)
        // Calcula o ângulo para o radar ficar travado no alvo
        double radarTurn = normalRelativeAngleDegrees(
            getHeading() + e.getBearing() - getRadarHeading()
        );
        
        // Adiciona um pequeno movimento extra para não perder o alvo
        // Se o radar está girando para a direita, adiciona +20, senão -20
        if (radarTurn > 0) {
            radarTurn += 20;
        } else {
            radarTurn -= 20;
        }
        setTurnRadarRight(radarTurn);

		// ✅ MIRA O CANHÃO COM PREDIÇÃO
        double gunTurnPreditivo = calcularAnguloPreditivo();
        setTurnGunRight(gunTurnPreditivo);
        
        out.println("Inimigo velocidade: " + e.getVelocity() + 
                   ", distância: " + e.getDistance() + 
                   ", girando canhão: " + gunTurnPreditivo);


        // Ajusta a força do disparo dependendo da distância
        if (e.getDistance() > 200) {
            lastFirePower = 1.5;
        } else if (e.getDistance() > 100) {
            lastFirePower = 2;
        } else {
            lastFirePower = 3;
        }

        wantToFire = true;
        
        // ✅ CORREÇÃO 5: REMOVIDO O scan() DAQUI
        // NUNCA chamar scan() dentro de eventos!
    }

    // ===== EVENTO: QUANDO BATE NA PAREDE =====
    public void onHitWall(HitWallEvent e) {
        out.println("BATEU NA PAREDE!");
        
        // Para imediatamente
        setAhead(0);
        
        // Vira 90 graus para o lado oposto da parede
        double bearing = e.getBearing();
        setTurnRight(90 - bearing);
        
        // Anda bastante para se afastar
        setAhead(200);
        
        // Reseta a espiral para começar pequena novamente
        passo = 10;
        expandindo = true;
        
        execute();
    }

    // ===== EVENTO: QUANDO BATE EM OUTRO ROBÔ =====
    public void onHitRobot(HitRobotEvent e) {
        double gunTurn = normalRelativeAngleDegrees(
            getHeading() + e.getBearing() - getGunHeading()
        );
        setTurnGunRight(gunTurn);

        if (getGunHeat() == 0) {
            setFire(3);
        }

        if (e.isMyFault()) {
            setTurnRight(10);
        }
    }

    // ===== EVENTO: QUANDO VENCE A PARTIDA =====
    public void onWin(WinEvent e) {
        // Dança da vitória
        for (int i = 0; i < 20; i++) {
            turnRight(30);
			turnLeft(30);
        }
    }
}