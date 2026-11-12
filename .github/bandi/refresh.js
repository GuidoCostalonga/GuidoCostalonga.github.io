import { writeFileSync } from "node:fs";
import { resolve } from "node:path";
//#region src/lib/bandi/comuni.ts
/** Comuni FVG usati per il filtro territoriale. I nomi più lunghi vincono sui prefissi. */
var COMUNI = [
	{
		prov: "TS",
		name: "Duino-Aurisina"
	},
	{
		prov: "TS",
		name: "San Dorligo della Valle"
	},
	{
		prov: "TS",
		name: "Monrupino"
	},
	{
		prov: "TS",
		name: "Sgonico"
	},
	{
		prov: "TS",
		name: "Muggia"
	},
	{
		prov: "TS",
		name: "Trieste"
	},
	{
		prov: "GO",
		name: "San Canzian d'Isonzo"
	},
	{
		prov: "GO",
		name: "San Pier d'Isonzo"
	},
	{
		prov: "GO",
		name: "San Lorenzo Isontino"
	},
	{
		prov: "GO",
		name: "Savogna d'Isonzo"
	},
	{
		prov: "GO",
		name: "Romans d'Isonzo"
	},
	{
		prov: "GO",
		name: "Gradisca d'Isonzo"
	},
	{
		prov: "GO",
		name: "Farra d'Isonzo"
	},
	{
		prov: "GO",
		name: "Fogliano Redipuglia"
	},
	{
		prov: "GO",
		name: "Ronchi dei Legionari"
	},
	{
		prov: "GO",
		name: "Doberdò del Lago"
	},
	{
		prov: "GO",
		name: "Capriva del Friuli"
	},
	{
		prov: "GO",
		name: "Dolegna del Collio"
	},
	{
		prov: "GO",
		name: "San Floriano del Collio"
	},
	{
		prov: "GO",
		name: "Mariano del Friuli"
	},
	{
		prov: "GO",
		name: "Staranzano"
	},
	{
		prov: "GO",
		name: "Monfalcone"
	},
	{
		prov: "GO",
		name: "Turriaco"
	},
	{
		prov: "GO",
		name: "Villesse"
	},
	{
		prov: "GO",
		name: "Cormons"
	},
	{
		prov: "GO",
		name: "Gorizia"
	},
	{
		prov: "GO",
		name: "Grado"
	},
	{
		prov: "GO",
		name: "Sagrado"
	},
	{
		prov: "GO",
		name: "Medea"
	},
	{
		prov: "GO",
		name: "Mossa"
	},
	{
		prov: "GO",
		name: "Moraro"
	},
	{
		prov: "PN",
		name: "San Giorgio della Richinvelda"
	},
	{
		prov: "PN",
		name: "Montereale Valcellina"
	},
	{
		prov: "PN",
		name: "Casarsa della Delizia"
	},
	{
		prov: "PN",
		name: "Pasiano di Pordenone"
	},
	{
		prov: "PN",
		name: "Prata di Pordenone"
	},
	{
		prov: "PN",
		name: "San Vito al Tagliamento"
	},
	{
		prov: "PN",
		name: "Sesto al Reghena"
	},
	{
		prov: "PN",
		name: "Roveredo in Piano"
	},
	{
		prov: "PN",
		name: "Valvasone Arzene"
	},
	{
		prov: "PN",
		name: "Azzano Decimo"
	},
	{
		prov: "PN",
		name: "Fontanafredda"
	},
	{
		prov: "PN",
		name: "Fiume Veneto"
	},
	{
		prov: "PN",
		name: "Spilimbergo"
	},
	{
		prov: "PN",
		name: "Cordenons"
	},
	{
		prov: "PN",
		name: "Pordenone"
	},
	{
		prov: "PN",
		name: "Maniago"
	},
	{
		prov: "PN",
		name: "Brugnera"
	},
	{
		prov: "PN",
		name: "Aviano"
	},
	{
		prov: "PN",
		name: "Zoppola"
	},
	{
		prov: "PN",
		name: "Porcia"
	},
	{
		prov: "PN",
		name: "Sacile"
	},
	{
		prov: "PN",
		name: "Caneva"
	},
	{
		prov: "PN",
		name: "Polcenigo"
	},
	{
		prov: "PN",
		name: "Andreis"
	},
	{
		prov: "PN",
		name: "Barcis"
	},
	{
		prov: "PN",
		name: "Arba"
	},
	{
		prov: "UD",
		name: "San Giovanni al Natisone"
	},
	{
		prov: "UD",
		name: "Fiumicello Villa Vicentina"
	},
	{
		prov: "UD",
		name: "Malborghetto-Valbruna"
	},
	{
		prov: "UD",
		name: "Colloredo di Monte Albano"
	},
	{
		prov: "UD",
		name: "Camino al Tagliamento"
	},
	{
		prov: "UD",
		name: "Palazzolo dello Stella"
	},
	{
		prov: "UD",
		name: "Castions di Strada"
	},
	{
		prov: "UD",
		name: "San Giorgio di Nogaro"
	},
	{
		prov: "UD",
		name: "San Daniele del Friuli"
	},
	{
		prov: "UD",
		name: "Cervignano del Friuli"
	},
	{
		prov: "UD",
		name: "Forgaria nel Friuli"
	},
	{
		prov: "UD",
		name: "Cividale del Friuli"
	},
	{
		prov: "UD",
		name: "Gemona del Friuli"
	},
	{
		prov: "UD",
		name: "Lignano Sabbiadoro"
	},
	{
		prov: "UD",
		name: "Muzzana del Turgnano"
	},
	{
		prov: "UD",
		name: "Terzo di Aquileia"
	},
	{
		prov: "UD",
		name: "Terzo d'Aquileia"
	},
	{
		prov: "UD",
		name: "Santa Maria la Longa"
	},
	{
		prov: "UD",
		name: "San Vito di Fagagna"
	},
	{
		prov: "UD",
		name: "San Vito al Torre"
	},
	{
		prov: "UD",
		name: "Pozzuolo del Friuli"
	},
	{
		prov: "UD",
		name: "Pasian di Prato"
	},
	{
		prov: "UD",
		name: "Reana del Rojale"
	},
	{
		prov: "UD",
		name: "Rivignano Teor"
	},
	{
		prov: "UD",
		name: "Rive d'Arcano"
	},
	{
		prov: "UD",
		name: "Corno di Rosazzo"
	},
	{
		prov: "UD",
		name: "Forni di Sopra"
	},
	{
		prov: "UD",
		name: "Forni di Sotto"
	},
	{
		prov: "UD",
		name: "Forni Avoltri"
	},
	{
		prov: "UD",
		name: "Prato Carnico"
	},
	{
		prov: "UD",
		name: "Treppo Ligosullo"
	},
	{
		prov: "UD",
		name: "Villa Santina"
	},
	{
		prov: "UD",
		name: "Bagnaria Arsa"
	},
	{
		prov: "UD",
		name: "Pavia di Udine"
	},
	{
		prov: "UD",
		name: "Mereto di Tomba"
	},
	{
		prov: "UD",
		name: "Marano Lagunare"
	},
	{
		prov: "UD",
		name: "Campoformido"
	},
	{
		prov: "UD",
		name: "Martignacco"
	},
	{
		prov: "UD",
		name: "Remanzacco"
	},
	{
		prov: "UD",
		name: "Tavagnacco"
	},
	{
		prov: "UD",
		name: "Tricesimo"
	},
	{
		prov: "UD",
		name: "Palmanova"
	},
	{
		prov: "UD",
		name: "Codroipo"
	},
	{
		prov: "UD",
		name: "Latisana"
	},
	{
		prov: "UD",
		name: "Tolmezzo"
	},
	{
		prov: "UD",
		name: "Tarvisio"
	},
	{
		prov: "UD",
		name: "Tarcento"
	},
	{
		prov: "UD",
		name: "Aquileia"
	},
	{
		prov: "UD",
		name: "Cercivento"
	},
	{
		prov: "UD",
		name: "Chiusaforte"
	},
	{
		prov: "UD",
		name: "Comeglians"
	},
	{
		prov: "UD",
		name: "Enemonzo"
	},
	{
		prov: "UD",
		name: "Arta Terme"
	},
	{
		prov: "UD",
		name: "Paularo"
	},
	{
		prov: "UD",
		name: "Paluzza"
	},
	{
		prov: "UD",
		name: "Pontebba"
	},
	{
		prov: "UD",
		name: "Ravascletto"
	},
	{
		prov: "UD",
		name: "Resiutta"
	},
	{
		prov: "UD",
		name: "Rigolato"
	},
	{
		prov: "UD",
		name: "Sappada"
	},
	{
		prov: "UD",
		name: "Socchieve"
	},
	{
		prov: "UD",
		name: "Mortegliano"
	},
	{
		prov: "UD",
		name: "Talmassons"
	},
	{
		prov: "UD",
		name: "Sedegliano"
	},
	{
		prov: "UD",
		name: "Treppo Grande"
	},
	{
		prov: "UD",
		name: "Basiliano"
	},
	{
		prov: "UD",
		name: "Bertiolo"
	},
	{
		prov: "UD",
		name: "Carlino"
	},
	{
		prov: "UD",
		name: "Coseano"
	},
	{
		prov: "UD",
		name: "Dignano"
	},
	{
		prov: "UD",
		name: "Fagagna"
	},
	{
		prov: "UD",
		name: "Flaibano"
	},
	{
		prov: "UD",
		name: "Lestizza"
	},
	{
		prov: "UD",
		name: "Majano"
	},
	{
		prov: "UD",
		name: "Moruzzo"
	},
	{
		prov: "UD",
		name: "Pocenia"
	},
	{
		prov: "UD",
		name: "Porpetto"
	},
	{
		prov: "UD",
		name: "Precenicco"
	},
	{
		prov: "UD",
		name: "Ragogna"
	},
	{
		prov: "UD",
		name: "Ronchis"
	},
	{
		prov: "UD",
		name: "Varmo"
	},
	{
		prov: "UD",
		name: "Ampezzo"
	},
	{
		prov: "UD",
		name: "Dogna"
	},
	{
		prov: "UD",
		name: "Lauco"
	},
	{
		prov: "UD",
		name: "Ovaro"
	},
	{
		prov: "UD",
		name: "Preone"
	},
	{
		prov: "UD",
		name: "Raveo"
	},
	{
		prov: "UD",
		name: "Resia"
	},
	{
		prov: "UD",
		name: "Sauris"
	},
	{
		prov: "UD",
		name: "Sutrio"
	},
	{
		prov: "UD",
		name: "Zuglio"
	},
	{
		prov: "UD",
		name: "Taipana"
	},
	{
		prov: "UD",
		name: "Lusevera"
	},
	{
		prov: "UD",
		name: "Povoletto"
	},
	{
		prov: "UD",
		name: "Nimis"
	},
	{
		prov: "UD",
		name: "Attimis"
	},
	{
		prov: "UD",
		name: "Faedis"
	},
	{
		prov: "UD",
		name: "Buja"
	},
	{
		prov: "UD",
		name: "Osoppo"
	},
	{
		prov: "UD",
		name: "Gemona"
	},
	{
		prov: "UD",
		name: "Manzano"
	},
	{
		prov: "UD",
		name: "Buttrio"
	},
	{
		prov: "UD",
		name: "Premariacco"
	},
	{
		prov: "UD",
		name: "Gonars"
	},
	{
		prov: "UD",
		name: "Torviscosa"
	},
	{
		prov: "UD",
		name: "Udine"
	}
];
//#endregion
//#region src/lib/bandi/classify.ts
var BENEFICIARY_RULES = [
	{
		id: "imprese",
		re: /impres|\bpmi\b|aziend|commerci|artigian|turistic/i
	},
	{
		id: "terzo",
		re: /volontariat|associaz|terzo settore|\bets\b|\bodv\b|onlus|fondazion|pro loco/i
	},
	{
		id: "enti",
		re: /enti local|unione di comuni|a favore dei comuni|comuni beneficiar/i
	},
	{
		id: "cittadini",
		re: /cittadin|famigl|\bgiovani\b|caregiver|persone con disabilit/i
	},
	{
		id: "lavoro",
		re: /lavorator|disoccup|collocamento|cantieri di lavoro|pubblica utilit|avviamento a selezione/i
	},
	{
		id: "formazione",
		re: /formazion|dottorat|universit|scolastic|istitut|\biefp\b|allievi|student/i
	},
	{
		id: "agro",
		re: /agricol|ittic|forestal|agroalim|seminativ|zootecn|\bpesca\b/i
	},
	{
		id: "sanita",
		re: /sanitar|medic[oi]|pediatr|ospedal|\basugi\b|\basfo\b|azienda sanitaria/i
	},
	{
		id: "casa",
		re: /alloggi|edilizia residenz|\bater\b/i
	}
];
function channelOf(draft) {
	const office = draft.office;
	if (draft.list === "ater" || /\/ater\//i.test(draft.url) || /\bater\b/i.test(office)) return "ater";
	if (draft.list === "cm" || /collocamento mirato/i.test(office)) return "collocamento";
	if (draft.list === "cpi" || /impiego/i.test(office)) return "cpi";
	return "regione";
}
function areaOf(draft, channel) {
	const office = draft.office;
	const title = draft.title;
	if (channel === "ater" || /\bater\b/i.test(office)) return "casa";
	if (/turismo|cultur|spettacol|musei|teatro/i.test(office)) return "turismo";
	if (/lavoro|formazione|impiego|istruzione|collocamento/i.test(office)) return "lavoro";
	if (/salute|social|disabilit/i.test(office)) return "salute";
	if (/ambiente|energia|sostenib/i.test(office)) return "ambiente";
	if (/infrastruttur|\bterritorio\b/i.test(office)) return "infrastrutture";
	if (/agroaliment|forest|ittic/i.test(office)) return "agro";
	if (/autonomie|sicurezza|immigra/i.test(office)) return "sicurezza";
	if (/patrimonio|demanio/i.test(office)) return "patrimonio";
	if (/cultur|spettacol|\bteatr|\bcinema|museo/i.test(title)) return "turismo";
	if (/agricol|forestal|ittic/i.test(title)) return "agro";
	return "altro";
}
function natureOf(title) {
	if (/graduatori|\besito\b|revoca (della )?procedura|revoca procedura/i.test(title)) return {
		nature: "Esito",
		outcome: true
	};
	if (/avviamento|concorso|selezione|assunzion|incarichi vacanti|lavori di pubblica utilit|cantieri di lavoro/i.test(title)) return {
		nature: "Selezione",
		outcome: false
	};
	if (/derivare acqua|demanio|concessione per l'utilizzo|opere di presa/i.test(title)) return {
		nature: "Concessione",
		outcome: false
	};
	if (/contribut|finanziament|incentiv|fondo perduto/i.test(title)) return {
		nature: "Contributo",
		outcome: false
	};
	if (/\bavviso\b/i.test(title)) return {
		nature: "Avviso",
		outcome: false
	};
	if (/\bbando\b/i.test(title)) return {
		nature: "Bando",
		outcome: false
	};
	return {
		nature: "Altro",
		outcome: false
	};
}
function fold(value) {
	return value.toLowerCase().normalize("NFD").replace(/\p{M}/gu, "").replace(/[’ʼ]/g, "'").replace(/[^a-z0-9' ]+/g, " ").replace(/\s+/g, " ").trim();
}
var COMUNE_INDEX = [...COMUNI].map((c) => ({
	prov: c.prov,
	name: fold(c.name)
})).filter((c) => c.name.length > 2).sort((a, b) => b.name.length - a.name.length);
var SKIP_IF_PRECEDED = { grado: /(?:secondo|primo|terzo|quarto)\s+$/ };
function detectPlaces(text) {
	const padded = ` ${fold(text)} `;
	const hits = [];
	for (const comune of COMUNE_INDEX) {
		const needle = ` ${comune.name} `;
		let from = 0;
		while (from < padded.length) {
			const at = padded.indexOf(needle, from);
			if (at < 0) break;
			const start = at + 1;
			const end = start + comune.name.length;
			const preceded = SKIP_IF_PRECEDED[comune.name];
			const falseHit = preceded ? preceded.test(padded.slice(0, start)) : false;
			if (!hits.some((hit) => start < hit.end && end > hit.start) && !falseHit) hits.push({
				start,
				end,
				prov: comune.prov
			});
			from = at + needle.length - 1;
		}
	}
	const places = new Set(hits.map((hit) => hit.prov));
	return places.size ? [...places] : ["FVG"];
}
function classify(draft) {
	const channel = channelOf(draft);
	const { nature, outcome } = natureOf(draft.title);
	const beneficiaries = BENEFICIARY_RULES.filter((rule) => rule.re.test(`${draft.title} ${draft.office}`)).map((rule) => rule.id);
	if (channel === "ater" && !beneficiaries.includes("casa")) beneficiaries.push("casa");
	if ((channel === "cpi" || channel === "collocamento") && !beneficiaries.includes("lavoro")) beneficiaries.push("lavoro");
	return {
		id: draft.url.split("/").pop()?.replace(/\.html$/i, "") || draft.url,
		title: draft.title,
		url: draft.url,
		published: draft.published,
		deadline: draft.deadline,
		office: draft.office || "Struttura non indicata",
		channel,
		area: areaOf(draft, channel),
		nature,
		beneficiaries,
		places: detectPlaces(`${draft.title} ${draft.office}`),
		contributivo: draft.contributivo || /contribut|finanziament|incentiv|fondo perduto/i.test(draft.title),
		outcome
	};
}
function mergeDrafts(drafts) {
	const byUrl = /* @__PURE__ */ new Map();
	for (const draft of drafts) {
		const prev = byUrl.get(draft.url);
		if (!prev) {
			byUrl.set(draft.url, draft);
			continue;
		}
		const prevGeneric = /centri per l'impiego/i.test(prev.office);
		const nextSpecific = !/centri per l'impiego/i.test(draft.office);
		const winner = prevGeneric && nextSpecific ? draft : prev;
		byUrl.set(draft.url, {
			...winner,
			contributivo: prev.contributivo || draft.contributivo,
			published: winner.published ?? prev.published ?? draft.published,
			deadline: winner.deadline ?? prev.deadline ?? draft.deadline
		});
	}
	return [...byUrl.values()];
}
//#endregion
//#region src/lib/bandi/parse.ts
var ORIGIN$1 = "https://www.regione.fvg.it";
var NAMED = {
	amp: "&",
	lt: "<",
	gt: ">",
	quot: "\"",
	apos: "'",
	nbsp: " ",
	agrave: "à",
	egrave: "è",
	eacute: "é",
	igrave: "ì",
	ograve: "ò",
	ugrave: "ù",
	Agrave: "À",
	Egrave: "È",
	auml: "ä",
	ouml: "ö",
	uuml: "ü",
	ldquo: "“",
	rdquo: "”",
	lsquo: "‘",
	rsquo: "’",
	ndash: "–",
	mdash: "—",
	hellip: "…"
};
function decodeHtml(input) {
	return input.replace(/<[^>]+>/g, " ").replace(/&(#x?[0-9a-f]+|[a-z]+);/gi, (all, body) => {
		if (body[0] === "#") {
			const code = body[1] === "x" || body[1] === "X" ? Number.parseInt(body.slice(2), 16) : Number.parseInt(body.slice(1), 10);
			return Number.isFinite(code) ? String.fromCodePoint(code) : all;
		}
		return NAMED[body] ?? NAMED[body.toLowerCase()] ?? all;
	}).replace(/\s+/g, " ").trim();
}
var MONTHS = {
	jan: "01",
	feb: "02",
	mar: "03",
	apr: "04",
	may: "05",
	jun: "06",
	jul: "07",
	aug: "08",
	sep: "09",
	oct: "10",
	nov: "11",
	dec: "12"
};
function parseDate(raw) {
	const text = raw.trim();
	const it = text.match(/^(\d{1,2})\.(\d{1,2})\.(\d{4})$/);
	if (it) {
		const d = it[1].padStart(2, "0");
		const m = it[2].padStart(2, "0");
		return `${it[3]}-${m}-${d}`;
	}
	const en = text.match(/^(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\s+([A-Za-z]{3})\s+(\d{1,2})\s+\d{2}:\d{2}:\d{2}\s+\w+\s+(\d{4})$/);
	if (en) {
		const month = MONTHS[en[1].toLowerCase()];
		if (!month) return null;
		return `${en[3]}-${month}-${en[2].padStart(2, "0")}`;
	}
	return null;
}
function maxPage(html) {
	const nums = [...html.matchAll(/[?&]pag=(\d+)/g)].map((m) => Number(m[1]));
	const max = nums.length ? Math.max(...nums) : 1;
	return Math.min(Math.max(max, 1), 40);
}
function parseListing(html, list) {
	const blocks = html.match(/<div class="box-link box box-bando">[\s\S]*?<\/a>\s*<\/div>/g);
	if (!blocks) return [];
	const drafts = [];
	for (const block of blocks) {
		const href = block.match(/href="([^"]+)"/);
		const title = block.match(/<h3>([\s\S]*?)<\/h3>/);
		if (!href || !title) continue;
		let url = href[1].replace(/&/g, "&");
		if (url.startsWith("//")) url = `https:${url}`;
		else if (url.startsWith("/")) url = `${ORIGIN$1}${url}`;
		const spans = [...(block.match(/<div class="box-header[\s\S]*?<\/div>\s*<\/div>/)?.[0] ?? "").matchAll(/<span[^>]*>([\s\S]*?)<\/span>/g)].map((m) => decodeHtml(m[1]));
		const dates = spans.map(parseDate).filter((d) => Boolean(d));
		const hasDeadline = spans.some((s) => /scadenza/i.test(s));
		const campo = block.match(/class="box-campo">([\s\S]*?)<\/div>/);
		const published = hasDeadline ? dates.length >= 2 ? dates[0] : null : dates[0] ?? null;
		const deadline = hasDeadline ? dates.length >= 2 ? dates[1] : dates[0] ?? null : null;
		drafts.push({
			title: decodeHtml(title[1]),
			url,
			published,
			deadline,
			office: campo ? decodeHtml(campo[1]) : "Struttura non indicata",
			contributivo: false,
			list
		});
	}
	return drafts;
}
//#endregion
//#region src/lib/bandi/fetch-live.ts
var ORIGIN = "https://www.regione.fvg.it";
var LIST = `${ORIGIN}/rafvg/cms/RAFVG/MODULI/bandi_avvisi/`;
var SEARCH = `${ORIGIN}/rafvg/cms/RAFVG/MODULI/bandi_avvisi/ricerca.jsp`;
var ATER = `${ORIGIN}/rafvg/cms/RAFVG/MODULI/ater`;
async function getText(url, init) {
	let last = "Portale non raggiungibile";
	for (let attempt = 0; attempt < 4; attempt += 1) {
		if (attempt) await new Promise((resolve) => setTimeout(resolve, 800 * attempt));
		const response = await fetch(url, {
			...init,
			headers: {
				"User-Agent": "BandiFVG/1.0 (catalogo di consultazione; costalonga.org/bandifvg)",
				Accept: "text/html",
				...init?.headers ?? {}
			},
			signal: AbortSignal.timeout(2e4)
		});
		if (response.ok) return response.text();
		last = `Portale non raggiungibile (${response.status})`;
		if (response.status !== 403 && response.status !== 429 && response.status < 500) break;
	}
	throw new Error(last);
}
async function pool(limit, jobs) {
	const out = new Array(jobs.length);
	let cursor = 0;
	async function worker() {
		while (cursor < jobs.length) {
			const index = cursor;
			cursor += 1;
			out[index] = await jobs[index]();
		}
	}
	const workers = Array.from({ length: Math.min(limit, jobs.length) }, () => worker());
	await Promise.all(workers);
	return out;
}
async function paged(firstUrl, pageUrl, list) {
	const first = await getText(firstUrl);
	const total = maxPage(first);
	const drafts = parseListing(first, list);
	if (total < 2) return drafts;
	const rest = await pool(6, Array.from({ length: total - 1 }, (_, index) => () => getText(pageUrl(index + 2))));
	for (const html of rest) drafts.push(...parseListing(html, list));
	return drafts;
}
async function contributivoUrls() {
	const first = await getText(SEARCH, {
		method: "POST",
		headers: { "Content-Type": "application/x-www-form-urlencoded" },
		body: new URLSearchParams({
			txtChiave: "",
			onlyTagServizio: "1",
			startsearch: "vai"
		}).toString()
	});
	const urls = new Set(parseListing(first, "regione").map((item) => item.url));
	const total = maxPage(first);
	if (total < 2) return urls;
	const rest = await pool(4, Array.from({ length: total - 1 }, (_, index) => {
		const page = index + 2;
		return () => getText(`${SEARCH}?txtChiave=&pag=${page}&onlyTagServizio=1`);
	}));
	for (const html of rest) for (const item of parseListing(html, "regione")) urls.add(item.url);
	return urls;
}
async function fetchLiveCatalog() {
	const [regione, cpi, cm, ater, contributi] = await Promise.all([
		paged(LIST, (page) => `${LIST}?pag=${page}`, "regione"),
		paged(`${LIST}?p=cpi`, (page) => `${LIST}?p=cpi&pag=${page}`, "cpi").catch(() => []),
		paged(`${LIST}?p=cm`, (page) => `${LIST}?p=cm&pag=${page}`, "cm").catch(() => []),
		getText(ATER).then((html) => parseListing(html, "ater")).catch(() => []),
		contributivoUrls().catch(() => /* @__PURE__ */ new Set())
	]);
	const merged = mergeDrafts([
		...regione,
		...cpi,
		...cm,
		...ater
	]);
	if (!merged.length) throw new Error("Il portale non ha restituito bandi.");
	for (const draft of merged) if (contributi.has(draft.url)) draft.contributivo = true;
	return {
		items: merged.map(classify).sort((a, b) => {
			return (b.published ?? "").localeCompare(a.published ?? "");
		}),
		fetchedAt: (/* @__PURE__ */ new Date()).toISOString(),
		source: "portale",
		stale: false
	};
}
//#endregion
//#region scripts/refresh-catalog.ts
var catalog = await fetchLiveCatalog();
var target = resolve(process.argv[2] ?? "src/lib/bandi/snapshot.ts");
if (target.endsWith(".json")) writeFileSync(target, `${JSON.stringify(catalog)}\n`);
else {
	const body = `import type { Catalog } from "./types";\n\nexport const archivedCatalog: Catalog = ${JSON.stringify(catalog, null, 2)};\n`;
	writeFileSync(target, body);
}
console.log(`catalogo aggiornato: ${catalog.items.length} bandi, ${catalog.fetchedAt}`);
//#endregion
export {};
