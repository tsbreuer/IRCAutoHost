package autohost.utils;

public class beatmapFile {
	public lt.ekgame.beatmap_analyzer.Beatmap beatmap;
	public int id;
	public double[] ppvalues;
	public beatmapFile(int bid){
		this.id = bid;
		this.ppvalues = new double[4];
	}
	public void setpp(double ss, double sshd, double sshr, double sshdhr){
		this.ppvalues[0] = ss;
		this.ppvalues[1] = sshd;
		this.ppvalues[2] = sshr;
		this.ppvalues[3] = sshdhr;
	}
	public void setpptab(double[] str) {
		if (str.length < 4)
			return;
		this.ppvalues[0] = str[0];
		this.ppvalues[1] = str[1];
		this.ppvalues[2] = str[2];
		this.ppvalues[3] = str[3];	
	}
}
