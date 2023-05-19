package prev23.phase.livean;

import prev23.data.mem.*;
import prev23.data.asm.*;
import prev23.phase.*;
import prev23.phase.asmgen.*;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Vector;

/**
 * Liveness analysis.
 */
public class LiveAn extends Phase {

	public LiveAn() {
		super("livean");
	}

	public void analysis() {
		for(Code c : AsmGen.codes)
			analyze(c);
	}

	public void analyze(Code c) {
		HashMap<MemLabel, AsmLABEL> labelsMap = new HashMap<>();

		for (AsmInstr instr : c.instrs) {
			if (instr instanceof AsmLABEL) {
				labelsMap.put(((AsmLABEL) instr).getLabel(), (AsmLABEL) instr);
			}
		}
		
		while(true) {
			boolean shouldBreak = true;
			Vector<AsmInstr> instrs = c.instrs;

			for(int i = 0; i < instrs.size(); i++) {
				AsmOPER oper = (AsmOPER)instrs.get(i);

				HashSet<MemTemp> liveIn = oper.out();
				HashSet<MemTemp> liveOut = new HashSet<>();

				oper.defs().forEach(liveIn::remove);
				liveIn.addAll(oper.uses());

				if(i == instrs.size() - 1)
					break;

				if(!oper.jumps().isEmpty() && !oper.instr().contains("PUSHJ")) {
					for(MemLabel label : oper.jumps()) {
						if(labelsMap.get(label) != null)
							liveOut.addAll(labelsMap.get(label).out());
					}
				}
				else {
					liveOut.addAll(instrs.get(i + 1).in());
				}

				if(!oper.in().equals(liveIn) || !oper.out().equals(liveOut))
					shouldBreak = false;

				oper.removeAllFromIn();
				oper.removeAllFromOut();

				oper.addInTemps(liveIn);
				oper.addOutTemp(liveOut);
			}

			if(shouldBreak)
				break;
		}
	}

	public void log() {
		if (logger == null)
			return;
		for (Code code : AsmGen.codes) {
			logger.begElement("code");
			logger.addAttribute("entrylabel", code.entryLabel.name);
			logger.addAttribute("exitlabel", code.exitLabel.name);
			logger.addAttribute("tempsize", Long.toString(code.tempSize));
			code.frame.log(logger);
			logger.begElement("instructions");
			for (AsmInstr instr : code.instrs) {
				logger.begElement("instruction");
				logger.addAttribute("code", instr.toString());
				logger.begElement("temps");
				logger.addAttribute("name", "use");
				for (MemTemp temp : instr.uses()) {
					logger.begElement("temp");
					logger.addAttribute("name", temp.toString());
					logger.endElement();
				}
				logger.endElement();
				logger.begElement("temps");
				logger.addAttribute("name", "def");
				for (MemTemp temp : instr.defs()) {
					logger.begElement("temp");
					logger.addAttribute("name", temp.toString());
					logger.endElement();
				}
				logger.endElement();
				logger.begElement("temps");
				logger.addAttribute("name", "in");
				for (MemTemp temp : instr.in()) {
					logger.begElement("temp");
					logger.addAttribute("name", temp.toString());
					logger.endElement();
				}
				logger.endElement();
				logger.begElement("temps");
				logger.addAttribute("name", "out");
				for (MemTemp temp : instr.out()) {
					logger.begElement("temp");
					logger.addAttribute("name", temp.toString());
					logger.endElement();
				}
				logger.endElement();
				logger.endElement();
			}
			logger.endElement();
			logger.endElement();
		}
	}

}
