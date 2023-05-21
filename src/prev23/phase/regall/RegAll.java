package prev23.phase.regall;

import java.util.*;

import prev23.data.mem.*;
import prev23.common.report.Report;
import prev23.data.asm.*;
import prev23.phase.*;
import prev23.phase.asmgen.*;
import prev23.phase.livean.*;


/**
 * Register allocation.
 */
public class RegAll extends Phase {

	/** Mapping of temporary variables to registers. */
	public final HashMap<MemTemp, Integer> tempToReg = new HashMap<MemTemp, Integer>();
	public final HashMap<MemTemp, Node> tempToNode = new HashMap<MemTemp, Node>();
	public final HashMap<MemTemp, Long> offsets = new HashMap<MemTemp, Long>();

	public int NUM_REGISTERS = 32;

	class Node {
		private HashSet<MemTemp> cNodes;
		private MemTemp temp;
		private int regNum;

		public Node(MemTemp temp) {
			this.temp = temp;
			this.regNum = -1;
			cNodes = new HashSet<MemTemp>();
		}

		// Getters
		public HashSet<MemTemp> getCNodes() {
			return cNodes;
		}

		public MemTemp getTemp() {
			return temp;
		}

		public int getReg() {
			return regNum;
		}

		public int numConns() {
			return cNodes.size();
		}

		// checking
		public boolean hasConnection(MemTemp temp) {
			return cNodes.contains(temp);
		}

		// adding
		public void addConnection(MemTemp conn) {
			cNodes.add(conn);
			cNodes.remove(temp);
		}

		public void addAllConnections(HashSet<MemTemp> conns) {
			cNodes.addAll(conns);
			cNodes.remove(temp);
		}

		// removing
		public void removeConnection(MemTemp conn) {
			cNodes.remove(conn);
		}

		public void removeConnection(Node node) {
			cNodes.remove(node.getTemp());
		}

		// coloring
		public boolean color(int maxColors) {
			boolean success = false;

			for(int i = 0; i < maxColors && !success; i++) {
				boolean valid = true;

				for(MemTemp temp : cNodes) {
					if(tempToReg.containsKey(temp) && tempToReg.get(temp).intValue() == i) {
						valid = false;
						break;
					}
				}

				if(valid) {
					success = true;
					regNum = i;
				}
			}

			return success;
		}
	}

	public RegAll() {
		super("regall");
	}

	public Vector<AsmInstr> saveNumber(long offset, MemTemp temp) {
		Vector<AsmInstr> saveVec = new Vector<>();
		Vector<MemTemp> defs = new Vector<>();

		defs.add(temp);
		long value = Math.abs(offset);

		saveVec.add(new AsmOPER(String.format("SETL `d0,%d", value & 0x000000000000FFFFL), null, defs, null));
		if((value & 0x00000000FFFF0000L) > 0)
			saveVec.add(new AsmOPER(String.format("INCML `d0,%d", ((value & 0x00000000FFFF0000L) >> 16)), defs, defs, null));
		if((value & 0x0000FFFF00000000L) > 0)
			saveVec.add(new AsmOPER(String.format("INCMH `d0,%d", ((value & 0x0000FFFF00000000L) >> 32)), defs, defs, null));
		if((value & 0xFFFF000000000000L) > 0)
			saveVec.add(new AsmOPER(String.format("INCH `d0,%d", ((value & 0xFFFF000000000000L) >> 48)), defs, defs, null));

		if (offset < 0)
			saveVec.add(new AsmOPER("NEG `d0,0,`s0", defs, defs, null));

		return saveVec;
	}

	public void allocate() {
		for(Code c : AsmGen.codes) {
			// try allocating until successful
			while(!allocateCode(c));

			offsets.clear();
		}
	}

	public boolean allocateCode(Code code) {
		Stack<Node> stack = new Stack<Node>();
		boolean colored = false;

		tempToNode.clear();

		// 1) Create graph
		for(AsmInstr instr : code.instrs) {
			HashSet<MemTemp> allTemps = instr.in();
			allTemps.addAll(instr.out());
			allTemps.addAll(instr.defs());

			for(MemTemp temp : allTemps) {
				if(!tempToNode.containsKey(temp)) {
					tempToNode.put(temp, new Node(temp));
				}

				if(tempToReg.containsKey(temp))
					tempToReg.remove(temp);

				tempToNode.get(temp).addAllConnections(allTemps);
			}
		}

		// 2) Simplify graph
		while(tempToNode.size() > 0) {
			boolean simplfiable = true;

			while(simplfiable) {
				simplfiable = false;

				for(Node node : tempToNode.values()) {
					if(node.numConns() < NUM_REGISTERS) {
						// very nice :)
						stack.push(node);

						// remove this node from all neighbours
						for(Node n : tempToNode.values())
							n.removeConnection(node);

						tempToNode.remove(node.getTemp());
						simplfiable = true;
						break;
					}
				}
			}

			// 3) Spill
			if(tempToNode.size() > 0) {
				for(Node node : tempToNode.values()) {
					if(node.numConns() >= NUM_REGISTERS) {
						stack.push(node);

						// remove this node from all neighbours
						for(Node n : tempToNode.values())
							n.removeConnection(node);

						tempToNode.remove(node.getTemp());
						break;	
					}
				}
			}
		}

		// 4) Select
		boolean succeeded = true;
		Node node = stack.peek();
		// Stack<Node> stack2 = new Stack<Node>();
		// stack2.addAll(stack);

		while(!stack.isEmpty() && succeeded) {
			node = stack.pop();

			// check if we have fp
			if(node.getTemp() == code.frame.FP) {
				tempToReg.put(node.getTemp(), Integer.valueOf(253));
				succeeded = true;
			}
			else {
				succeeded = node.color(NUM_REGISTERS);

				if(succeeded)
					tempToReg.put(node.getTemp(), Integer.valueOf(node.getReg()));
			}
		}

		// 5) Have fun :)
		if(!succeeded) {
			Vector<AsmInstr> modifiedInstrs = new Vector<>();
			modifiedInstrs.addAll(code.instrs);

			MemTemp changeTemp = node.getTemp();

			for(AsmInstr instr : code.instrs) {
				int pos = modifiedInstrs.indexOf(instr);
				modifiedInstrs.remove(pos);

				MemTemp replacementTemp = new MemTemp();
				MemTemp offsetsTemp = new MemTemp();


				if(instr.defs().contains(changeTemp)) {
					// Modify defines
					Vector<MemTemp> newDefs = instr.defs();
					newDefs.remove(changeTemp);
					newDefs.add(replacementTemp);

					Vector<MemTemp> uses = new Vector<>();
					Vector<MemTemp> defs = new Vector<>();

					defs.add(offsetsTemp);

					uses.add(replacementTemp);
					uses.add(code.frame.FP);
					uses.add(offsetsTemp);

					code.tempSize += 8;
					long tempOffset = -code.tempSize - code.frame.locsSize - 16;

					offsets.put(changeTemp, Long.valueOf(tempOffset)); 

					AsmInstr newInstr = new AsmOPER(((AsmOPER)instr).instr(), instr.uses(), newDefs, instr.jumps());
					Vector<AsmInstr> numDInstr = saveNumber(tempOffset, offsetsTemp);

					AsmInstr store = new AsmOPER("STO `s0,`s1,`s2", uses, null, null);
					modifiedInstrs.insertElementAt(newInstr, pos);

					int iterator = 1;
					for(AsmInstr tmpInstr : numDInstr) {
						modifiedInstrs.insertElementAt(tmpInstr, pos + iterator);
						iterator++;
					}

					modifiedInstrs.insertElementAt(store, pos + iterator);
				}
				else if(instr.uses().contains(changeTemp)) {
					Vector<MemTemp> newUses = instr.uses();
					newUses.remove(changeTemp);
					newUses.add(replacementTemp);

					Vector<MemTemp> uses = new Vector<>();
					Vector<MemTemp> defs = new Vector<>();
					Vector<MemTemp> defsLoad = new Vector<>();

					defs.add(offsetsTemp);
					defsLoad.add(replacementTemp);

					uses.add(code.frame.FP);
					uses.add(offsetsTemp);

					long tempOffset = offsets.get(changeTemp).longValue();
					Vector<AsmInstr> numDInstr = saveNumber(tempOffset, offsetsTemp);

					AsmInstr load = new AsmOPER("LDO `d0,`s0,`s1", uses, defsLoad, null);
					AsmInstr newInstr = new AsmOPER(((AsmOPER)instr).instr(), newUses, instr.defs(), instr.jumps());

					int iterator = 0;
					for(AsmInstr tmpInstr : numDInstr) {
						modifiedInstrs.insertElementAt(tmpInstr, pos + iterator);
						iterator++;
					}

					modifiedInstrs.insertElementAt(load, pos + iterator);
					modifiedInstrs.insertElementAt(newInstr, pos + iterator + 1);
				}
			}

			// clear current instructions and add mofified ones
			code.instrs.clear();
			code.instrs.addAll(modifiedInstrs);

			// reanalyze code
			LiveAn livean = new LiveAn();
			livean.analyze(code);
			colored = false;
		}
		// we have succeeded
		else {
			colored = true;
		}

		return colored;
	}

	public void log() {
		if (logger == null)
			return;

		// tempToReg.forEach((key, value) -> System.out.println(key + " " + value));
		for (Code code : AsmGen.codes) {
			logger.begElement("code");
			logger.addAttribute("entrylabel", code.entryLabel.name);
			logger.addAttribute("exitlabel", code.exitLabel.name);
			logger.addAttribute("tempsize", Long.toString(code.tempSize));
			code.frame.log(logger);
			logger.begElement("instructions");
			for (AsmInstr instr : code.instrs) {
				logger.begElement("instruction");
				logger.addAttribute("code", instr.toString(tempToReg));
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
