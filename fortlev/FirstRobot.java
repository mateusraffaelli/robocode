package fortlev;

import robocode.*;
import static robocode.util.Utils.normalRelativeAngleDegrees;
import java.awt.*;

/**
 * AndersonSilva - a robot by (Arthur Abdala, Arthur de Oliveira, Mateus Raffaelli e Matheus Posada)
 */
public class FirstRobot extends AdvancedRobot {

    // ===== VARIÁVEIS DE CONTROLE =====
    double velocidadeRadar = 30; // velocidade de rotação do radar (em graus por tick)
    boolean girandoDireita = true; // controla o sentido de rotação do radar
    double passo = 1.0;            // distância que o robô anda a cada iteração (controla o raio da espiral)
    double incremento = 0.08;      // quanto o "passo" aumenta ou diminui (abre/fecha a espiral)
    double angTurn = 3.0;          // ângulo que o corpo gira por ciclo
    boolean expandindo = true;     // indica se a espiral está abrindo (true) ou fechando (false)
	int count = 0; // Keeps track of how long we've
	// been searching for our target
	double gunTurnAmt;
	String trackName; // Name of the robot we're currently tracking
	boolean wantToFire = false;    // Se quer atirar no alvo atual
	double lastFirePower = 3;      // potência que for usar

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
		
		// Prepara a arma
		trackName = null; // Initialize to not tracking anyone
		gunTurnAmt = 10;

			

        // === LOOP PRINCIPAL (executa até o fim da partida) ===
       while (true) {
    
			// Ajustar passo para gerar espiral (expandir/contrair)
            if (expandindo) {
                passo += incremento;
                if (passo > 150) expandindo = false;
            } else {
                passo -= incremento;
                if (passo < 10) {
                    expandindo = true;
                    // desloca o centro do espiral para evitar ficar preso no mesmo lugar
                    setTurnRight(45);
                    setAhead(100);
                    execute();
                }
            }

            // 2) varredura contínua do radar
            // Giro longo para procurar robôs. Como é set*, não bloqueia o movimento.
            setTurnRadarRight(360);

            // 3) varredura/variação do canhão enquanto procura
            setTurnGunRight(gunTurnAmt);
			
			execute(); // verificar se faz sentido manter

		    // --- checagem para atirar ---
		    // Só atira se:
		    // 1) temos alvo (trackName != null)
		    // 2) queremos atirar (wantToFire == true)
		    // 3) o canhão está praticamente alinhado (getGunTurnRemaining() pequeno)
		    // Opcionalmente: checar também se o radar está alinhado com o alvo (getRadarTurnRemaining()).
		
		    double gunRemaining = Math.abs(getGunTurnRemaining());   // graus restantes para alinhar a gun
		    double radarRemaining = Math.abs(getRadarTurnRemaining()); // se quiser verificar o radar também
		
		    // threshold: quantos graus de tolerância aceitar antes de atirar
		    double GUN_ANGLE_TOLERANCE = 3.0;    // 3 graus costuma ser suficiente
		
		    if (trackName != null && wantToFire && gunRemaining <= GUN_ANGLE_TOLERANCE) {
		        // gun alinhado -> atira
		        fire(lastFirePower);
		        wantToFire = false; // reset flag para não atirar repetidamente
		    }


            // lógica de "procura" de alvo usando count
            count++; // cada iteração sem onScannedRobot incrementa

            if (count > 2) gunTurnAmt = -10;
            if (count > 5) gunTurnAmt = 10;
            if (count > 11) trackName = null; // depois de muito tempo sem ver, libera alvo

             // === EVITAR PAREDES ===
            // Se o robô estiver próximo das bordas do campo de batalha, muda de direção
            if (getX() < 100 || getX() > getBattleFieldWidth() - 100 ||
                getY() < 100 || getY() > getBattleFieldHeight() - 100) {
                setTurnRight(90);
                setAhead(150);
                execute();
            }

            // === CONTROLE DO RADAR ===
            // Gira o radar continuamente para procurar inimigos
            if (girandoDireita)
                setTurnRadarRight(velocidadeRadar);
            else
                setTurnRadarLeft(velocidadeRadar);

            // A cada 50 "ticks", muda o sentido e a velocidade do radar para dar dinamismo
            if (getTime() % 50 == 0) {
                girandoDireita = !girandoDireita;
                velocidadeRadar = 5 + Math.random() * 40; // velocidade aleatória entre 5° e 45°
            }

            execute(); // executa todos os comandos pendentes sem travar o loop
        }
    } 


    // ===== EVENTO: QUANDO DETECTA UM INIMIGO =====
    public void onScannedRobot(ScannedRobotEvent e) {
		
		// Calcula quanto o radar e o canhão precisam girar para mirar no inimigo
        double radarTurn = normalRelativeAngleDegrees(getHeading() + e.getBearing() - getRadarHeading());
        double gunTurn = normalRelativeAngleDegrees(getHeading() + e.getBearing() - getGunHeading());
		
        // Ajusta o radar e o canhão na direção do inimigo (sem bloquear o movimento)
        setTurnRadarRight(radarTurn);
        setTurnGunRight(gunTurn);
		
		// Identifica/define alvo como antes
		if (trackName != null && !e.getName().equals(trackName)) {
			return;
		}

		// Caso não tenha um alvo, agora define
		if (trackName == null) {
			trackName = e.getName();
			out.println("Tracking " + trackName);
		}
		// O alvo foi encontrado.  Reseta o contador 
		count = 0;

		// Ajusta a força do dispado dependendo da distancia
		if (e.getDistance() > 200) {
        	lastFirePower = 1;
			setTurnRight(45);
			setAhead(100);
    	} else if (e.getDistance() > 100) {
        	lastFirePower = 2;
			setTurnRight(45);
			setAhead(100);
    	} else {
        	lastFirePower = 3;
			setTurnRight(45);
			setAhead(100);
    	}

    	wantToFire = true;
		



		scan(); //scan ta dando conflito e pode causar recursão infinita ou travamento
	}

    // ===== EVENTO: QUANDO BATE NA PAREDE =====
    public void onHitWall(HitWallEvent e) {
        double bearing = e.getBearing(); // ângulo em que bateu
        setTurnRight(-bearing);          // vira para o lado oposto
        setAhead(100);                   // se afasta da parede
    }

    // ===== EVENTO: QUANDO BATE EM OUTRO ROBÔ =====
    public void onHitRobot(HitRobotEvent e) {
        // Se o inimigo estiver bem à frente, dispara
        // Calcula o ângulo que o canhão precisa girar para mirar no inimigo
   		 double gunTurn = normalRelativeAngleDegrees(getHeading() + e.getBearing() - getGunHeading());

    	// Gira o canhão em direção ao inimigo (sem bloquear o movimento)
    	setTurnGunRight(gunTurn);

   	 	// Atira após alinhar o canhão
   		 if (getGunHeat() == 0) {
        	setFire(3); // potência do tiro
    	}

   		 // Se a colisão foi culpa do nosso robô, desvia um pouco para não travar
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