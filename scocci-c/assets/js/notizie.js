/* ==========================================================
   ARCHIVIO NOTIZIE
   Per aggiungere un articolo: copiare un blocco { ... } in cima
   all'elenco e compilare i campi.
   - data: formato AAAA-MM-GG; se vuota compare "DA VERIFICARE".
   - categoria: "ambiente" | "energia" | "territorio" | "eventi"
   - verificato: true solo dopo il controllo su fonte ufficiale.
   - fonte: indirizzo della fonte (comunicato regionale, testata).
   ========================================================== */
window.NOTIZIE = [
  {
    id: "manovra-estiva-2026",
    titolo: "Manovra estiva: quasi 103 milioni per ambiente, energia e sviluppo sostenibile",
    categoria: "energia",
    data: "2026-07-23",
    verificato: true,
    fonte: "https://www.italpress.com/?p=719803",
    fonteNome: "Italpress, 23 luglio 2026",
    immagine: "assets/img/notizie/assestamento.jpg",
    sintesi: "Con la manovra estiva l’impegno complessivo 2026 sulle deleghe ambientali ed energetiche supera i 300 milioni di euro: dalle sedi di ARPA FVG alle comunità energetiche, fino alla difesa del suolo.",
    testo: [
      "Il pacchetto destinato all’ambiente, all’energia e allo sviluppo sostenibile nella manovra estiva vale quasi 103 milioni di euro. Sommato agli stanziamenti già previsti, porta l’impegno finanziario complessivo del 2026 oltre quota 300 milioni.",
      "Le voci principali: 6 milioni per il piano delle sedi e le nuove strumentazioni di ARPA FVG (Agenzia regionale per la protezione dell’ambiente del Friuli Venezia Giulia); 7 milioni ad AUSIR (Autorità unica per i servizi idrici e i rifiuti) per le infrastrutture dei rifiuti e il servizio idrico integrato; 3,7 milioni per i centri di raccolta comunali, per un totale di legislatura di quasi 50 milioni.",
      "Sul fronte energetico arrivano 8 milioni per le comunità energetiche rinnovabili (3 milioni per Comuni e Università, 5 milioni per le imprese), 2 milioni per la sostituzione dei generatori di calore, 5 milioni per lo sconto carburanti fino a fine 2026 e 500 mila euro per il rifacimento delle cabine elettriche delle imprese.",
      "Previsti inoltre 7 milioni per l’efficientamento ecologico degli impianti sportivi, 4 milioni alla cabina di regia per la difesa del suolo (circa 39 milioni l’impegno totale sul rischio idrogeologico), 775 mila euro per la bonifica dell’ex inceneritore di Gorizia e 500 mila euro per i polmoni verdi urbani.",
      "Risposta immediata anche all’emergenza delle sostanze perfluoroalchiliche (PFAS) nel Pordenonese: 2 milioni per nuovi acquedotti, analisi sui pozzi e contributi alle famiglie per filtri domestici o allacci idrici."
    ],
    galleria: [
      { src: "assets/img/notizie/assestamento.jpg", didascalia: "In Consiglio regionale per l’assestamento" },
      { src: "assets/img/energia.svg", didascalia: "Comunità energetiche rinnovabili" },
      { src: "assets/img/sopralluogo.svg", didascalia: "Difesa del suolo" }
    ]
  },
  {
    id: "ia-mare-barcolana",
    titolo: "Intelligenza artificiale e tutela del mare: la Regione alla Barcolana con gli studenti del Nautico",
    categoria: "eventi",
    data: "",
    verificato: false,
    fonte: "",
    immagine: "assets/img/ia-mare.svg",
    sintesi: "Tecnologia al servizio dell’ecosistema marino: un’iniziativa educativa nel cuore della Barcolana che unisce giovani, innovazione e amore per il Golfo.",
    testo: [
      "La Barcolana non è soltanto la regata più partecipata: è l’occasione per parlare di mare a chi il mare lo vivrà domani. Con gli studenti dell’Istituto Nautico di Trieste la Regione ha presentato strumenti di intelligenza artificiale applicati al monitoraggio e alla salvaguardia dell’ambiente marino.",
      "L’obiettivo è avvicinare i ragazzi alle professioni del futuro, mostrando come la tecnologia possa aiutare a leggere lo stato di salute delle acque e a prevenire i rischi per l’ecosistema del Golfo.",
      "Testo di presentazione da completare con i dati ufficiali dell’iniziativa (data, partner, strumenti impiegati)."
    ],
    galleria: [
      { src: "assets/img/mare-giovani.svg", didascalia: "Studenti in mare" },
      { src: "assets/img/ia-mare.svg", didascalia: "Monitoraggio con intelligenza artificiale" },
      { src: "assets/img/barcola.jpg", didascalia: "Il Golfo di Trieste" }
    ]
  },
  {
    id: "sin-torviscosa",
    titolo: "Bonifiche: passo avanti per il Sito di Interesse Nazionale di Torviscosa",
    categoria: "ambiente",
    data: "",
    verificato: false,
    fonte: "",
    immagine: "assets/img/bonifiche.svg",
    sintesi: "Risanare le aree industriali significa restituire terreni sicuri alle comunità e nuove possibilità di sviluppo al territorio.",
    testo: [
      "Il Sito di Interesse Nazionale (SIN) di Torviscosa è una delle partite ambientali più rilevanti per il Friuli Venezia Giulia. La Regione ha lavorato per sbloccare l’iter di bonifica, in raccordo con il Ministero competente e con gli enti locali.",
      "Accanto alle bonifiche prosegue il rafforzamento di ARPA FVG (Agenzia regionale per la protezione dell’ambiente del Friuli Venezia Giulia), con nuove sedi e strumentazioni per controlli sempre più puntuali.",
      "Testo da completare con gli estremi degli atti e lo stato di avanzamento ufficiale dell’iter."
    ],
    galleria: [
      { src: "assets/img/bonifiche.svg", didascalia: "Area industriale" },
      { src: "assets/img/sopralluogo.svg", didascalia: "Sopralluogo tecnico" }
    ]
  },
  {
    id: "enpa-efficientamento",
    titolo: "Benessere animale: sostegno all’efficientamento energetico delle sedi ENPA",
    categoria: "territorio",
    data: "",
    verificato: false,
    fonte: "",
    immagine: "assets/img/comunita.svg",
    sintesi: "Sedi più efficienti significano meno costi in bolletta e più risorse per la cura degli animali accolti dai volontari.",
    testo: [
      "Chi si prende cura degli animali abbandonati merita strutture adeguate. Per questo la Regione sostiene l’efficientamento energetico delle sedi dell’ENPA (Ente nazionale protezione animali) presenti sul territorio.",
      "Un intervento concreto che unisce due obiettivi: ridurre i consumi e liberare risorse da destinare al benessere animale e all’attività dei volontari.",
      "Testo da completare con sedi interessate e importi ufficiali."
    ],
    galleria: [
      { src: "assets/img/comunita.svg", didascalia: "Una sede di accoglienza" }
    ]
  },
  {
    id: "nuova-costa-barcola",
    titolo: "“La nuova costa di Barcola”: presentato il progetto per il lungomare di Trieste",
    categoria: "territorio",
    data: "2023-11-16",
    verificato: true,
    fonte: "https://intrieste.com/?p=22207",
    fonteNome: "InTrieste, 16 novembre 2023",
    immagine: "assets/img/barcola.jpg",
    sintesi: "Un progetto in due parti, lato mare e linea di costa, da realizzare per fasi senza togliere ai triestini l’uso della spiaggia durante l’estate.",
    testo: [
      "L’assessore regionale Fabio Scoccimarro ha illustrato a istituzioni e associazioni di categoria il progetto “La nuova costa di Barcola: progetti, idee e futuro”, elaborato dallo studio Metroarea con l’architetto Giulio Paladini.",
      "Il progetto si articola in due parti destinate a confluire in un’unica realizzazione: il lato mare, dedicato a sicurezza, balneazione e strutture collegate; la linea di costa, con strade e parcheggi. Sono previste nuove baie, la protezione della costa e spazi per gli sport acquatici.",
      "Le priorità indicate sono il rispetto delle tradizioni dei residenti, l’attrattiva turistica, l’accessibilità per le persone più fragili e spazi dedicati agli animali.",
      "L’iter prevede l’adozione di una variante al Piano regolatore comunale, la redazione dei progetti esecutivi, le gare d’appalto e l’attuazione graduale dei lavori, con un orizzonte complessivo di circa vent’anni."
    ],
    galleria: [
      { src: "assets/img/barcola.jpg", didascalia: "La costa di Barcola" },
      { src: "assets/img/mare-giovani.svg", didascalia: "Sport acquatici" },
      { src: "assets/img/conferenza.svg", didascalia: "Presentazione del progetto" }
    ]
  },
  {
    id: "cooperazione-golfo",
    titolo: "Un Golfo, tre Paesi: cooperazione ambientale con Slovenia e Croazia",
    categoria: "ambiente",
    data: "",
    verificato: false,
    fonte: "",
    immagine: "assets/img/mare-giovani.svg",
    sintesi: "Il mare non conosce confini: la tutela dell’Alto Adriatico passa da un lavoro comune con i Paesi vicini.",
    testo: [
      "La qualità delle acque e la sicurezza della navigazione nel Golfo di Trieste riguardano allo stesso modo Italia, Slovenia e Croazia. La Regione promuove tavoli di cooperazione per condividere dati, buone pratiche e strategie di intervento.",
      "Testo da completare con gli accordi o gli incontri ufficiali di riferimento."
    ],
    galleria: [
      { src: "assets/img/mare-giovani.svg", didascalia: "Alto Adriatico" }
    ]
  },
  {
    id: "impianti-sportivi-storici",
    titolo: "Impianti sportivi storici più efficienti e più sostenibili",
    categoria: "eventi",
    data: "2026-07-23",
    verificato: true,
    fonte: "https://www.italpress.com/?p=719803",
    fonteNome: "Italpress, 23 luglio 2026",
    immagine: "assets/img/notizie/sgt-nautica.jpg",
    sintesi: "Nella manovra estiva 7 milioni di euro per l’efficientamento ecologico degli impianti sportivi: luoghi di comunità che meritano di durare nel tempo.",
    testo: [
      "Le società sportive sono presidi di comunità, soprattutto per i più giovani. Con la manovra estiva 2026 la Regione ha destinato 7 milioni di euro all’efficientamento ecologico degli impianti sportivi.",
      "Ridurre i consumi energetici delle strutture significa abbassare i costi di gestione e mettere le società nelle condizioni di investire su attività, allenatori e ragazzi."
    ],
    galleria: [
      { src: "assets/img/comunita.svg", didascalia: "Strutture per la comunità" }
    ]
  },
  {
    id: "energia-imprese",
    titolo: "Energia e competitività: diversificare le fonti per sostenere famiglie e imprese",
    categoria: "energia",
    data: "",
    verificato: false,
    fonte: "",
    immagine: "assets/img/energia.svg",
    sintesi: "Una strategia energetica che guarda ai costi reali di chi lavora e produce, senza scorciatoie ideologiche.",
    testo: [
      "La transizione energetica funziona se è sostenibile anche nei conti di famiglie e imprese. La strategia regionale punta sulla diversificazione delle fonti, sulle comunità energetiche rinnovabili e sul sostegno al tessuto produttivo per ridurre i costi.",
      "Testo da completare con i riferimenti al Piano energetico regionale e alle misure attive."
    ],
    galleria: [
      { src: "assets/img/energia.svg", didascalia: "Fonti rinnovabili" }
    ]
  }
];

/* Notizie dal notiziario «La passione di sempre» (2026). Generato da notiziario.py */
window.NOTIZIE_NOTIZIARIO = [
  {
    "id": "droni-al-posto-dei-fuochi",
    "titolo": "Droni al posto dei fuochi d’artificio: la misura diventa strutturale",
    "categoria": "eventi",
    "data": "2026-09-15",
    "periodo": "Settembre 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», settembre 2026",
    "immagine": "assets/img/notizie/droni-settembre.jpg",
    "sintesi": "Per il secondo anno la Regione sostiene con 100 mila euro ogni capoluogo che sceglie gli spettacoli di droni: zero emissioni e niente rumore per persone fragili e animali.",
    "testo": [
      "Per il secondo anno consecutivo la Regione sostiene con 100 mila euro ogni capoluogo del Friuli Venezia Giulia che sceglie i droni al posto dei fuochi d’artificio, rendendo la misura strutturale.",
      "L’obiettivo è incentivare eventi innovativi che azzerano emissioni e impatto acustico, a tutela delle persone fragili e degli animali. A Trieste, il 18 agosto, 20.000 spettatori hanno gremito le Rive per uno spettacolo entusiasmante: la dimostrazione che ecologia e turismo possono crescere insieme.",
      "I prossimi eventi finanziati saranno a Pordenone (4 settembre), Gorizia (18 dicembre) e Udine (31 dicembre)."
    ],
    "galleria": [
      {
        "src": "assets/img/notizie/droni-settembre.jpg",
        "didascalia": "Lo spettacolo di droni sulle Rive di Trieste"
      },
      {
        "src": "assets/img/notizie/droni-estate.jpg",
        "didascalia": "Il pubblico allo spettacolo di Trieste"
      }
    ]
  },
  {
    "id": "dissesto-idrogeologico-45-cantieri",
    "titolo": "Dissesto idrogeologico: 15,5 milioni ai Comuni per 45 cantieri",
    "categoria": "ambiente",
    "data": "2026-09-15",
    "periodo": "Settembre 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», settembre 2026",
    "immagine": "assets/img/notizie/dissesto.jpg",
    "sintesi": "Con il nuovo riparto dell’assestamento la Regione finanzia 45 cantieri, compresi 8 interventi necessari dal 2025. Oltre 1,5 miliardi destinati dalla Giunta Fedriga alla difesa del suolo.",
    "testo": [
      "La sicurezza dei cittadini e del territorio è una priorità assoluta: la Giunta Fedriga ha destinato oltre 1,5 miliardi di euro al contrasto del dissesto idrogeologico.",
      "Con il nuovo riparto dell’ultima legge di assestamento arrivano circa 15,5 milioni di euro ai Comuni del Friuli Venezia Giulia: finanziano 45 cantieri cruciali per le comunità locali e coprono anche 8 interventi necessari dal 2025.",
      "Grazie alla semplificazione normativa le procedure sono più snelle e il valore degli interventi finanziati è quadruplicato tra il 2025 e il 2026."
    ],
    "galleria": []
  },
  {
    "id": "lni-trieste-grand-prix",
    "titolo": "LNI Trieste Grand Prix: tre regate, un Golfo, tre Paesi",
    "categoria": "eventi",
    "data": "2026-09-15",
    "periodo": "Settembre 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», settembre 2026",
    "immagine": "assets/img/notizie/lni-grand-prix.jpg",
    "sintesi": "Il nuovo progetto della Lega Navale unisce tre regate del Golfo, avvicina i giovani alla tutela del mare e coinvolge Slovenia e Croazia.",
    "testo": [
      "Il nuovo “LNI Trieste Grand Prix” riunisce tre regate del Golfo in un’unica iniziativa e accresce il valore sportivo di Trieste e di tutta la regione.",
      "Un ringraziamento alla Lega Navale Italiana (LNI) per l’opera meritoria di avvicinare i giovani alla tutela dell’ambiente marino. Coinvolgendo Slovenia e Croazia, il progetto dimostra che il mare riesce a unire ciò che la storia ha diviso."
    ],
    "galleria": []
  },
  {
    "id": "misure-contributive-2026",
    "titolo": "Legge di stabilità 2026: le misure per famiglie, ambiente e sport",
    "categoria": "territorio",
    "data": "2026-09-15",
    "periodo": "Settembre 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», settembre 2026",
    "immagine": "assets/img/notizie/famiglie.jpg",
    "sintesi": "Fotovoltaico e solare termico, sconto carburanti, trasporto pubblico per gli over 65, Dote Famiglia e contributi alle società sportive: le principali linee di sostegno regionali.",
    "testo": [
      "Con la Legge di stabilità 2026 la Regione ha confermato misure cruciali per l’ambiente e il bilancio familiare: incentivi per il fotovoltaico e il solare termico dedicati a privati, condomini ed enti, lo sconto regionale sui carburanti, i fondi per l’efficientamento energetico e lo sconto sul trasporto pubblico locale per gli over 65.",
      "Le politiche per la famiglia offrono un sostegno in ogni fase della vita: Dote Famiglia e Carta Famiglia, aiuti per neomamme e giovani coppie, sostegni per abbattere il mutuo sulla prima casa e sussidi per i pensionati.",
      "Per lo sport dilettantistico sono previsti contributi per manifestazioni, attrezzature, automezzi e manutenzione degli impianti, con un’attenzione speciale allo sport paralimpico, all’inclusione e allo Sport Bonus regionale per le erogazioni liberali."
    ],
    "galleria": [
      {
        "src": "assets/img/notizie/misure-contributive.jpg",
        "didascalia": "Le misure contributive della Regione"
      }
    ]
  },
  {
    "id": "pallamano-trieste-serie-a",
    "titolo": "Pallamano Trieste in Serie A: 17 scudetti e una storia da rilanciare",
    "categoria": "eventi",
    "data": "2026-09-15",
    "periodo": "Settembre 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», settembre 2026",
    "immagine": "assets/img/notizie/pallamano.jpg",
    "sintesi": "Alla presentazione della nuova stagione, il sostegno della Regione a una società che ha fatto la storia della pallamano italiana.",
    "testo": [
      "Alla presentazione della Pallamano Trieste per la nuova stagione in Serie A, l’Assessore ha ribadito il sostegno della Regione alle società sportive, premiando chi si impegna anche nella formazione giovanile.",
      "La squadra vanta ben 17 scudetti ed è un’eccellenza: merita di tornare ai fasti di un tempo, quando non vincere faceva quasi notizia."
    ],
    "galleria": []
  },
  {
    "id": "san-luigi-calcio",
    "titolo": "San Luigi Calcio, una scuola di sport e di vita",
    "categoria": "eventi",
    "data": "2026-09-15",
    "periodo": "Settembre 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», settembre 2026",
    "immagine": "assets/img/notizie/san-luigi.jpg",
    "sintesi": "Una società radicata nel territorio, attenta ai giovani e competitiva: la Regione continuerà a esserle al fianco.",
    "testo": [
      "Il San Luigi Calcio è una realtà preziosa per Trieste e per tutto il Friuli Venezia Giulia: coniuga l’attenzione ai giovani con un forte radicamento nel territorio, mantenendo alta competitività e una preparazione atletica di primo livello.",
      "Lo sport insegna ai ragazzi ad affrontare vittorie e sconfitte, a porsi obiettivi e a crescere. Tutto questo è possibile grazie all’impegno quotidiano di dirigenti, famiglie e sponsor: la Regione continuerà a sostenere realtà come il San Luigi per la loro funzione sociale ed educativa."
    ],
    "galleria": []
  },
  {
    "id": "mese-alzheimer",
    "titolo": "Mese mondiale dell’Alzheimer: i palazzi regionali si illuminano di viola",
    "categoria": "territorio",
    "data": "2026-09-15",
    "periodo": "Settembre 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», settembre 2026",
    "immagine": "assets/img/notizie/alzheimer.jpg",
    "sintesi": "Il 21 settembre la Regione aderisce a “M’illumino di Viola” e rinnova il sostegno alla Fondazione de Banfield.",
    "testo": [
      "Per il Mese mondiale dell’Alzheimer la Regione rinnova il sostegno alla Fondazione de Banfield. Il 21 settembre aderisce a “M’illumino di Viola”, illuminando i palazzi regionali di piazza Oberdan e piazza Unità: un gesto semplice ma essenziale di vicinanza ai malati, alle famiglie e al personale socio sanitario.",
      "La Regione sostiene anche la prevenzione, come i test cognitivi gratuiti per gli over 60 a CasaViola, e resta al fianco del Terzo settore perché nessuna famiglia si senta sola."
    ],
    "galleria": []
  },
  {
    "id": "paola-del-din",
    "titolo": "Auguri a Paola Del Din, Medaglia d’oro al Valor militare",
    "categoria": "territorio",
    "data": "2026-09-15",
    "periodo": "Settembre 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», settembre 2026",
    "immagine": "assets/img/notizie/paola-del-din.jpg",
    "sintesi": "Il 22 agosto l’omaggio alla prima donna paracadutista italiana, unica Medaglia d’oro al Valor militare ancora in vita.",
    "testo": [
      "“Chiamatemi Patriota, non partigiana!”. Nata nel 1923, Paola Del Din è l’unica Medaglia d’oro al Valor militare ancora in vita e la prima donna paracadutista italiana.",
      "Con la sua storia ha insegnato che cosa significhi rischiare la vita per valori che vanno oltre ogni colore politico. La sua saggezza e il suo coraggio restano una fonte inesauribile di ispirazione, insieme a un’intera vita dedicata all’educazione e alla famiglia."
    ],
    "galleria": []
  },
  {
    "id": "canoa-trieste-grado-marano",
    "titolo": "In canoa da Trieste a Marano: un’idea di turismo lento",
    "categoria": "eventi",
    "data": "2026-09-15",
    "periodo": "Settembre 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», settembre 2026",
    "immagine": "assets/img/notizie/canoa.jpg",
    "sintesi": "Un vecchio amore ritrovato con il campione Paolo Nonis e il suo progetto di turismo lento lungo la costa.",
    "testo": [
      "La canoa K1, un vecchio amore di qualche anno fa, riscoperta insieme al campione Paolo Nonis.",
      "Molto interessante il suo progetto di turismo lento Trieste, Grado, Marano in canoa: un modo di vivere la costa del Friuli Venezia Giulia nel rispetto dell’ambiente."
    ],
    "galleria": []
  },
  {
    "id": "nuove-province",
    "titolo": "Tornano le Province: istituzioni di nuovo vicine ai cittadini",
    "categoria": "territorio",
    "data": "2026-07-15",
    "periodo": "Estate 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», estate 2026",
    "immagine": "assets/img/notizie/consiglio-regionale.jpg",
    "sintesi": "Con il ritorno delle Province di Trieste, Udine, Gorizia e Pordenone si chiude una battaglia portata avanti da oltre vent’anni.",
    "testo": [
      "Il Friuli Venezia Giulia riparte dai territori: con il ritorno delle Province di Trieste, Udine, Gorizia e Pordenone le istituzioni tornano vicine ai cittadini.",
      "È una battaglia che l’Assessore porta avanti da oltre vent’anni: già da Presidente della Provincia di Trieste sosteneva che le Province dovessero avere più competenze, più autonomia e risorse adeguate, perché i servizi funzionano meglio quando le decisioni si prendono vicino ai cittadini.",
      "Grazie a un emendamento decisivo di Fratelli d’Italia, le nuove Province non saranno scatole vuote: un passo importante per rafforzare i territori e costruire istituzioni più efficienti."
    ],
    "galleria": []
  },
  {
    "id": "ex-aree-militari-fotovoltaico",
    "titolo": "Dalle ex aree militari nuovi poli per l’energia pulita",
    "categoria": "energia",
    "data": "2026-07-15",
    "periodo": "Estate 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», estate 2026",
    "immagine": "assets/img/notizie/ex-aree-militari.jpg",
    "sintesi": "Spazi già infrastrutturati e abbandonati diventeranno impianti fotovoltaici, senza consumare suolo.",
    "testo": [
      "Le ex aree militari dismesse diventeranno un tassello centrale della strategia energetica regionale. Essendo già dotate di infrastrutture, sono la base ideale per impianti fotovoltaici che non consumano suolo.",
      "La Giunta ha avviato il percorso per stringere accordi con i Comuni e recuperare questi beni; a breve sarà lanciata una manifestazione d’interesse per individuare i soggetti privati interessati.",
      "L’obiettivo del Piano energetico è chiaro: più sicurezza e indipendenza, coinvolgendo enti locali, privati e comunità energetiche."
    ],
    "galleria": []
  },
  {
    "id": "transizione-energia-pragmatica",
    "titolo": "Transizione energetica: scienza e pragmatismo, non ideologia",
    "categoria": "energia",
    "data": "2026-07-15",
    "periodo": "Estate 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», estate 2026",
    "immagine": "assets/img/notizie/transizione.jpg",
    "sintesi": "Prudenza su Krško per il rischio sismico, interesse per i piccoli reattori di nuova generazione, idroelettrico di nuovo pubblico con FVG Energia.",
    "testo": [
      "Al convegno “Transizione. Energia, la crescita oltre le incertezze” l’Assessore ha ribadito che la transizione ecologica deve bilanciare le necessità dell’ambiente con quelle dello sviluppo economico del territorio.",
      "Sul raddoppio della centrale di Krško, in Slovenia, la posizione è di prudenza: in quell’area esiste un forte rischio sismico. Più interessanti i piccoli reattori modulari di quarta generazione, più agili, veloci da realizzare e sicuri.",
      "Il vero “oro” del Friuli Venezia Giulia è l’acqua: la Regione è stata la prima a varare una legge per riportare sotto il controllo pubblico le grandi concessioni idroelettriche in scadenza. Con la neonata FVG Energia la ricchezza prodotta dai bacini resterà sul territorio.",
      "Sul fotovoltaico sono stati investiti centinaia di milioni, con contributi fino al 90% delle spese per famiglie e imprese."
    ],
    "galleria": []
  },
  {
    "id": "accordo-arpa-porto",
    "titolo": "Accordo tra ARPA e Porto: ambiente e sviluppo insieme",
    "categoria": "ambiente",
    "data": "2026-07-15",
    "periodo": "Estate 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», estate 2026",
    "immagine": "assets/img/notizie/arpa-porto.jpg",
    "sintesi": "Fino al 2030 ARPA FVG monitorerà acque, aria e suolo; il Porto condividerà in anticipo le sue strategie per ridurre gli impatti.",
    "testo": [
      "L’intesa tra l’Agenzia regionale per la protezione dell’ambiente (ARPA FVG) e l’Autorità portuale riflette una linea precisa: tutelare l’ambiente senza trasformare le regole in ostacoli allo sviluppo. L’ex area a caldo di Servola dimostra che risanamento e rilancio occupazionale possono procedere insieme.",
      "Fino al 2030 ARPA monitorerà acque, aria e suolo e il Porto condividerà in anticipo le proprie strategie per mitigare gli impatti: un porto competitivo con un ecosistema sano.",
      "Un ricordo speciale va a Laura Schiozzi: chiudere oggi questo accordo significa onorare il suo grande impegno."
    ],
    "galleria": []
  },
  {
    "id": "ecoeventi-fvg-regolamento",
    "titolo": "EcoEventiFVG: 2 milioni per feste, sagre e gare a basso impatto",
    "categoria": "ambiente",
    "data": "2026-07-15",
    "periodo": "Estate 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», estate 2026",
    "immagine": "assets/img/notizie/ecoeventi.jpg",
    "sintesi": "Contributi fino al 70% delle spese, con un massimo di 20 mila euro per evento e due eventi all’anno.",
    "testo": [
      "Con il nuovo regolamento “EcoEventiFVG” la Regione premia chi organizza eventi nel rispetto dell’ambiente: eventi sportivi, agonistici e non, sagre, feste e concerti.",
      "Sono disponibili 2 milioni di euro. I contributi coprono il 70% delle spese ammesse, fino a 20 mila euro per evento e per un massimo di due eventi all’anno.",
      "Si finanziano erogatori alla spina, stoviglie compostabili, borracce, recupero del cibo contro gli sprechi e laboratori di sensibilizzazione. Con il ddl multisettoriale il bando diventa inoltre un procedimento a sportello, con rimborsi a consuntivo."
    ],
    "galleria": []
  },
  {
    "id": "triskell",
    "titolo": "Triskell, il festival celtico che rispetta l’ambiente",
    "categoria": "eventi",
    "data": "2026-07-15",
    "periodo": "Estate 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», estate 2026",
    "immagine": "assets/img/notizie/triskell.jpg",
    "sintesi": "Ventisei edizioni, ospiti internazionali e l’adesione agli Ecoeventi FVG: la Regione sostiene e patrocina la manifestazione.",
    "testo": [
      "Il Triskell si è ritagliato un rilievo mondiale, con ospiti internazionali e un pubblico di appassionati sempre più numeroso. La Regione lo sostiene e ne apprezza la grande attenzione per l’ambiente, confermata dall’adesione agli Ecoeventi FVG.",
      "Ventisei edizioni sono la riprova di una rassegna che centra i suoi obiettivi: musica, corsi, enogastronomia e giochi all’insegna della cultura celtica, con il patrocinio della Regione."
    ],
    "galleria": []
  },
  {
    "id": "widget-uv-arpa",
    "titolo": "Il nuovo strumento UV di ARPA FVG: prevenzione comune per comune",
    "categoria": "ambiente",
    "data": "2026-07-15",
    "periodo": "Estate 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», estate 2026",
    "immagine": "assets/img/notizie/widget-uv.jpg",
    "sintesi": "Previsioni dell’Indice UV per ogni Comune e consigli di protezione immediati, gratuiti e multilingua.",
    "testo": [
      "Il nuovo strumento sull’Indice UV di ARPA FVG trasforma dati complessi in un’informazione utile: fornisce le previsioni per singolo Comune e, quando il rischio supera quota 3, suggerisce con icone immediate le protezioni giuste.",
      "Il servizio è gratuito, multilingua e si può integrare nei siti di alberghi, lidi e uffici turistici, per garantire a tutti una prevenzione consapevole."
    ],
    "galleria": []
  },
  {
    "id": "elettrosmog-2025",
    "titolo": "Elettrosmog: nel 2025 tutti i valori entro la norma",
    "categoria": "ambiente",
    "data": "2026-07-15",
    "periodo": "Estate 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», estate 2026",
    "immagine": "assets/img/notizie/elettrosmog.jpg",
    "sintesi": "2.400 misurazioni di ARPA FVG senza alcun superamento dei limiti. Nuove centraline in continuo, a partire dal ricreatorio Cobolli.",
    "testo": [
      "Sull’inquinamento elettromagnetico è fondamentale sgombrare il campo da ogni allarmismo: nel 2025 ARPA FVG ha effettuato 2.400 misurazioni senza alcun superamento dei limiti di legge, che in Italia restano tra i più rigorosi d’Europa.",
      "Il 45% dei 2.866 impianti regionali ha già il 5G: lo sviluppo non avverrà mai a discapito della sicurezza. Ai monitoraggi puntuali si affiancano rilevazioni in continuo, con centraline nei punti nevralgici per almeno due settimane, a partire dal ricreatorio Cobolli di Trieste, per tutelare prima di tutto le persone più fragili."
    ],
    "galleria": []
  },
  {
    "id": "balneazione-secondo-posto",
    "titolo": "Acque di balneazione: il Friuli Venezia Giulia è secondo in Italia",
    "categoria": "ambiente",
    "data": "2026-07-15",
    "periodo": "Estate 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», estate 2026",
    "immagine": "assets/img/notizie/balneazione.jpg",
    "sintesi": "I dati del Sistema nazionale per la protezione dell’ambiente premiano gli investimenti regionali sulla depurazione.",
    "testo": [
      "I dati del Sistema nazionale per la protezione dell’ambiente (SNPA) certificano un risultato straordinario: il Friuli Venezia Giulia è al secondo posto in Italia per la qualità delle acque di balneazione, a un soffio dalla Puglia.",
      "Non è un caso, ma il frutto dei massicci investimenti regionali sui sistemi di depurazione. I campionamenti confermano l’eccellenza: cittadini e turisti godranno di un mare sicuro."
    ],
    "galleria": []
  },
  {
    "id": "siot-emissioni-odorigene",
    "titolo": "SIOT: nessun rischio per la salute, ma gli odori vanno risolti",
    "categoria": "ambiente",
    "data": "2026-07-15",
    "periodo": "Estate 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», estate 2026",
    "immagine": "assets/img/notizie/siot.jpg",
    "sintesi": "Il benzene è dieci volte sotto i limiti; servono interventi strutturali sui serbatoi di San Dorligo.",
    "testo": [
      "Il dialogo con SIOT TAL è costante e la Regione apprezza l’impegno a investire per ridurre i disagi olfattivi. I dati ARPA sono chiari: non ci sono pericoli per la salute pubblica e il benzene è dieci volte sotto i limiti di legge.",
      "Le emissioni odorigene restano però una criticità reale. San Dorligo è un nodo energetico strategico per l’Europa, ma questo non giustifica i disagi: per questo si attendono interventi strutturali sui serbatoi, incrociando i dati con l’azienda per trovare le migliori soluzioni nel rispetto dei cittadini."
    ],
    "galleria": []
  },
  {
    "id": "trieste-capitale-sport",
    "titolo": "Trieste è la capitale dello sport in Italia",
    "categoria": "eventi",
    "data": "2026-07-15",
    "periodo": "Estate 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», estate 2026",
    "immagine": "assets/img/notizie/capitale-sport.jpg",
    "sintesi": "L’Indice di sportività 2026 del Sole 24 Ore premia Trieste per atleti tesserati, sport femminile e sport d’acqua.",
    "testo": [
      "L’Indice di sportività 2026 del Sole 24 Ore parla chiaro: Trieste è la vera capitale dello sport in Italia, con primati assoluti per atleti tesserati e sport femminile e, quest’anno, anche negli sport d’acqua.",
      "La Regione continuerà a sostenere le società: lo sport è salute e orgoglio per la comunità."
    ],
    "galleria": []
  },
  {
    "id": "sara-lombardi-titolo",
    "titolo": "Boxe: la triestina Sara Lombardi campionessa italiana",
    "categoria": "eventi",
    "data": "2026-07-15",
    "periodo": "Estate 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», estate 2026",
    "immagine": "assets/img/notizie/sara-lombardi.jpg",
    "sintesi": "Al torneo internazionale dell’Ardita Trieste il titolo italiano nei supermosca.",
    "testo": [
      "Il torneo internazionale organizzato dall’Ardita Trieste ha confermato la città come punto di riferimento per il pugilato, con un movimento vivo e capace di coinvolgere giovani e famiglie.",
      "Complimenti a Sara Lombardi per il titolo italiano nei supermosca e un grazie a società, tecnici e volontari che promuovono i valori educativi e di sacrificio della nobile arte."
    ],
    "galleria": []
  },
  {
    "id": "pallacanestro-patrimonio",
    "titolo": "La pallacanestro è patrimonio del territorio",
    "categoria": "eventi",
    "data": "2026-07-15",
    "periodo": "Estate 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», estate 2026",
    "immagine": "assets/img/notizie/pallacanestro.jpg",
    "sintesi": "Il cuore di una città espresso nella sua squadra non può essere trasferito altrove: pieno sostegno della Regione al basket.",
    "testo": [
      "Lo sport è maestro di vita e il patrimonio di una società è un bene morale inalienabile. Grazie a tecnici e famiglie per l’impegno con i giovani.",
      "Sui titoli sportivi un punto resta fermo: il cuore di una città espresso nella sua squadra non può essere trasferito altrove. La Regione rinnova il suo totale sostegno a tutto il movimento cestistico."
    ],
    "galleria": []
  },
  {
    "id": "restauro-sgt-nautica",
    "titolo": "Restauro della SGT Nautica: 150 anni di remo nel Golfo",
    "categoria": "eventi",
    "data": "2026-07-15",
    "periodo": "Estate 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», estate 2026",
    "immagine": "assets/img/notizie/sgt-nautica.jpg",
    "sintesi": "Il recupero della sede della Società Ginnastica Triestina Nautica rilancia una realtà storica della città.",
    "testo": [
      "La Regione incentiva lo sport per agonisti, bambini e amatori. Il restauro della sede della Società Ginnastica Triestina (SGT) Nautica valorizza 150 anni di storia del remo nel Golfo e rilancia una realtà cittadina storica.",
      "Il sostegno a vela e canottaggio continuerà, perché i circoli triestini siano tra i più belli d’Italia."
    ],
    "galleria": []
  },
  {
    "id": "cresecup-domio",
    "titolo": "CreseCup: oltre 400 partite a Domio",
    "categoria": "eventi",
    "data": "2026-07-15",
    "periodo": "Estate 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», estate 2026",
    "immagine": "assets/img/notizie/cresecup.jpg",
    "sintesi": "La 24ª edizione ha coinvolto migliaia di sportivi tra calcio, pallavolo e pugilato.",
    "testo": [
      "Oltre 400 partite tra calcio, pallavolo e pugilato a Domio hanno coinvolto migliaia di sportivi nella 24ª CreseCup.",
      "Un plauso a volontari e organizzatori: la manifestazione si conferma un punto di riferimento per lo sport e l’aggregazione di ogni età."
    ],
    "galleria": []
  },
  {
    "id": "ricreatorio-san-pio-x-rozzol",
    "titolo": "Rinasce il ricreatorio San Pio X a Rozzol",
    "categoria": "territorio",
    "data": "2026-07-15",
    "periodo": "Estate 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», estate 2026",
    "immagine": "assets/img/notizie/rozzol.jpg",
    "sintesi": "Dopo oltre mezzo secolo di degrado, un intervento ecosostenibile da circa 1,8 milioni, con 460 mila euro dalla Regione.",
    "testo": [
      "Dopo oltre mezzo secolo di degrado il ricreatorio San Pio X torna a nuova vita grazie a una riqualificazione urbana ecosostenibile: aule, spazi pastorali e un campo polifunzionale per pallacanestro e pallavolo, a due passi dalle scuole Giotti e Stuparich.",
      "L’opera, sbloccata grazie alla collega Cristina Amirante, è sostenuta dalla Regione con 460 mila euro su un totale di circa 1,8 milioni.",
      "“Essendo nato e cresciuto nella valle di Rozzol, sono orgoglioso che questo luogo si trasformi finalmente nel cuore pulsante della comunità.”"
    ],
    "galleria": []
  },
  {
    "id": "alpini-adunata-trieste",
    "titolo": "Gli Alpini e la candidatura di Trieste alla 100ª Adunata",
    "categoria": "territorio",
    "data": "2026-07-15",
    "periodo": "Estate 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», estate 2026",
    "immagine": "assets/img/notizie/alpini.jpg",
    "sintesi": "Lo spirito di servizio degli Alpini, decisivo nel sisma del 1976, è un valore da trasmettere ai giovani.",
    "testo": [
      "Lo spirito di servizio degli Alpini è un valore da trasmettere ai giovani: lo dimostrò il loro ruolo decisivo nel terremoto del 1976.",
      "Per questo la Regione sostiene con forza la candidatura di Trieste a ospitare la 100ª Adunata nazionale."
    ],
    "galleria": []
  },
  {
    "id": "ricordo-giorgio-rossi",
    "titolo": "In ricordo di Giorgio Rossi",
    "categoria": "territorio",
    "data": "2026-07-15",
    "periodo": "Estate 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», estate 2026",
    "immagine": "assets/img/notizie/giorgio-rossi.jpg",
    "sintesi": "Trieste perde un amministratore appassionato e un amico sincero.",
    "testo": [
      "Con la scomparsa di Giorgio Rossi Trieste perde un amministratore appassionato e un amico sincero. La città si è stretta in un commosso abbraccio trasversale per rendergli omaggio.",
      "Stavamo lavorando insieme a progetti importanti per il futuro della comunità. Di lui resteranno l’entusiasmo, il sorriso indimenticabile e una straordinaria passione civile."
    ],
    "galleria": []
  },
  {
    "id": "viaggio-del-ricordo",
    "titolo": "Viaggio del Ricordo: i giovani sui luoghi del confine orientale",
    "categoria": "territorio",
    "data": "2026-07-15",
    "periodo": "Estate 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», estate 2026",
    "immagine": "assets/img/notizie/viaggio-ricordo.jpg",
    "sintesi": "Oltre cinquanta ragazzi coinvolti nell’iniziativa del Governo e del ministro Abodi sulla memoria delle foibe e dell’esodo.",
    "testo": [
      "L’iniziativa del Governo e del ministro Abodi per il “Viaggio del Ricordo” promuove un percorso di memoria fondamentale per superare il silenzio del passato.",
      "La legge ha diffuso in tutta Italia la consapevolezza sull’esodo giuliano dalmata e sulle foibe, ma restano resistenze e negazionismi. Coinvolgere oltre cinquanta giovani nei luoghi del confine orientale è la strada giusta: la storia non si cancella e le nuove generazioni devono conoscere la verità."
    ],
    "galleria": []
  },
  {
    "id": "amianto-mappatura",
    "titolo": "Amianto: 27,7 milioni dal 2018 e la mappatura completa entro il 2027",
    "categoria": "ambiente",
    "data": "2026-05-15",
    "periodo": "Maggio 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», maggio 2026",
    "immagine": "assets/img/notizie/amianto.jpg",
    "sintesi": "Censiti oltre 26.500 manufatti con droni e intelligenza artificiale; coperte tutte le domande idonee di contributo per le bonifiche.",
    "testo": [
      "Dal 2018 la Regione ha finanziato interventi per la rimozione dell’amianto per 27,7 milioni di euro. Entro il 2027 sarà completata la mappatura dell’amianto in Friuli Venezia Giulia.",
      "Sono già stati censiti oltre 26.500 manufatti e mappati i Comuni più grandi con droni e intelligenza artificiale, con un investimento di 750 mila euro. L’archivio è condiviso con Protezione civile e Vigili del fuoco.",
      "Per le bonifiche, dal 2017 sono stati erogati contributi per quasi 28 milioni di euro, di cui 7,5 a bilancio solo quest’anno, garantendo la copertura a tutte le domande idonee. L’obiettivo è una regione a “zero amianto”."
    ],
    "galleria": []
  },
  {
    "id": "ddl-multisettoriale-2026",
    "titolo": "Ddl multisettoriale 2026: semplificazione, ristori e transizione energetica",
    "categoria": "territorio",
    "data": "2026-05-15",
    "periodo": "Maggio 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», maggio 2026",
    "immagine": "assets/img/notizie/misure-contributive.jpg",
    "sintesi": "EcoEventiFVG a sportello, aiuti alla ristorazione contro la plastica, ristori per il porticciolo di Barcola, bando stufe retroattivo.",
    "testo": [
      "Con il disegno di legge (ddl) multisettoriale 2026 la Regione dà risposte concrete a cittadini e imprese.",
      "Il bando EcoEventiFVG diventa un procedimento a sportello con rimborsi a consuntivo, per agevolare associazioni e sport. Le microimprese della ristorazione ricevono contributi fino a 5.000 euro per la “deplastificazione” e la sterilizzazione del vetro da riutilizzare.",
      "Al porticciolo di Barcola, dopo il ritrovamento di 17 ordigni bellici, d’intesa con le associazioni nautiche sono stati istituiti ristori per rimborsare ai proprietari delle imbarcazioni le spese per i ricoveri alternativi.",
      "Il “Bando stufe” diventa retroattivo di dodici mesi: fino a 4.000 euro per sostituire i vecchi impianti a biomassa con pompe di calore o sistemi moderni. Sostegno anche alla Pordenone Sustainability Week 2026 e alla fruizione gratuita delle grotte turistiche a settembre. Intanto la Comunità energetica rinnovabile regionale si è costituita come Fondazione."
    ],
    "galleria": []
  },
  {
    "id": "trieste-trasporti-ricarica-broletto",
    "titolo": "Trieste Trasporti: al Broletto la nuova stazione di ricarica elettrica",
    "categoria": "energia",
    "data": "2026-05-15",
    "periodo": "Maggio 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», maggio 2026",
    "immagine": "assets/img/notizie/ricarica-broletto.jpg",
    "sintesi": "1,8 milioni per 12 stalli; entro il 2030 sarà elettrica metà della flotta, 130 autobus.",
    "testo": [
      "Con l’entrata in funzione della nuova stazione di ricarica elettrica al Broletto si apre una stagione nuova per la mobilità di Trieste: un’infrastruttura da 1,8 milioni di euro per 12 stalli.",
      "Il cronoprogramma guarda al 2030 con un obiettivo chiaro: portare all’elettrico metà della flotta di Trieste Trasporti, ben 130 autobus, per meno inquinamento e una mobilità più sostenibile ed efficiente.",
      "Trieste Trasporti, di cui l’Assessore è stato vicepresidente, è un’azienda all’avanguardia in Italia e in Europa, cresciuta investendo in sicurezza, innovazione e qualità del servizio."
    ],
    "galleria": []
  },
  {
    "id": "primato-rinnovabili",
    "titolo": "Rinnovabili: il Friuli Venezia Giulia è già al 46% del traguardo 2030",
    "categoria": "energia",
    "data": "2026-05-15",
    "periodo": "Maggio 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», maggio 2026",
    "immagine": "assets/img/notizie/fotovoltaico.jpg",
    "sintesi": "Lo studio Ciro con ISPRA colloca la regione tra le più virtuose d’Italia, ben oltre la media nazionale del 31%.",
    "testo": [
      "Il Friuli Venezia Giulia si conferma un’eccellenza nazionale: lo studio Ciro, realizzato in collaborazione con l’Istituto superiore per la protezione e la ricerca ambientale (ISPRA), lo colloca tra le regioni più virtuose per la transizione climatica.",
      "La regione ha già raggiunto il 46% del traguardo sulle rinnovabili fissato al 2030, contro una media nazionale del 31%.",
      "Un risultato nato da scelte concrete: incentivi senza precedenti per il fotovoltaico e il successo delle Comunità energetiche rinnovabili, che costruiscono un modello di vera democrazia energetica."
    ],
    "galleria": []
  },
  {
    "id": "polmoni-verdi-urbani",
    "titolo": "Dieci milioni per i polmoni verdi urbani",
    "categoria": "ambiente",
    "data": "2026-05-15",
    "periodo": "Maggio 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», maggio 2026",
    "immagine": "assets/img/notizie/polmoni-verdi.jpg",
    "sintesi": "Un investimento concreto contro i cambiamenti climatici, che fa bene alla natura e alle persone.",
    "testo": [
      "“L’uomo deve essere sempre al centro del creato, non in contrasto con esso.”",
      "La Regione ha stanziato 10 milioni di euro per creare nuovi polmoni verdi urbani: un investimento concreto contro i cambiamenti climatici, che fa bene alla natura, all’uomo e allo spirito."
    ],
    "galleria": []
  },
  {
    "id": "mare-nordest",
    "titolo": "Mare Nordest: Trieste capitale della cultura blu",
    "categoria": "eventi",
    "data": "2026-05-15",
    "periodo": "Maggio 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», maggio 2026",
    "immagine": "assets/img/notizie/mare-nordest.jpg",
    "sintesi": "I Tridenti d’Oro della subacquea, il centenario della rotta per idrovolanti Trieste Torino e l’educazione dei giovani al mare con ARPA FVG.",
    "testo": [
      "Trieste ha con il mare un rapporto viscerale. Per questo la Regione conferma il sostegno a “Mare Nordest”, che rende la città la capitale della cultura blu e ospita i Tridenti d’Oro, veri e propri “Nobel” della subacquea.",
      "Quest’anno si uniscono la memoria, dal centenario della storica rotta per idrovolanti Trieste Torino al ricordo degli eroi istriani, e una visione per il futuro: insieme ad ARPA FVG i giovani vengono educati alla tutela, alla biodiversità e all’amore per il mare Adriatico."
    ],
    "galleria": []
  },
  {
    "id": "sconto-carburanti-24-ore",
    "titolo": "Sconto carburanti: 30 mila richieste in tre mesi, ora si punta al 24 su 24",
    "categoria": "energia",
    "data": "2026-05-15",
    "periodo": "Maggio 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», maggio 2026",
    "immagine": "assets/img/notizie/sconto-carburanti.jpg",
    "sintesi": "Con la gestione diretta regionale un risparmio medio di 104 euro a cittadino; prossimo passo i distributori automatici.",
    "testo": [
      "In soli tre mesi di gestione diretta regionale lo sconto carburanti ha registrato oltre 30 mila richieste, superando il totale annuo precedente: un successo che premia la scelta di digitalizzare e rendere gratuito il servizio.",
      "Con risparmi medi di 104 euro a cittadino, anche grazie al taglio delle accise del Governo, il Friuli Venezia Giulia è tornato competitivo rispetto ai territori oltreconfine, contrastando il “turismo del pieno” e sostenendo le stazioni di servizio.",
      "L’obiettivo ora è estendere lo sconto ai distributori automatici, per garantire convenienza 24 ore su 24."
    ],
    "galleria": []
  },
  {
    "id": "sport-transizione-coni",
    "titolo": "Lo sport motore della transizione ecologica",
    "categoria": "eventi",
    "data": "2026-05-15",
    "periodo": "Maggio 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», maggio 2026",
    "immagine": "assets/img/notizie/coni.jpg",
    "sintesi": "Con il Comitato olimpico nazionale italiano (CONI) le società sportive diventano ambasciatrici di sostenibilità.",
    "testo": [
      "Al presidente del Comitato olimpico nazionale italiano (CONI) Luciano Bonfiglio l’Assessore ha sottolineato come, insieme, le società sportive stiano diventando ambasciatrici di sostenibilità: dagli eco eventi all’efficientamento energetico e idrico degli impianti, per ridurre consumi e costi.",
      "L’obiettivo è un sistema sportivo verde, esempio concreto di tutela del territorio."
    ],
    "galleria": []
  },
  {
    "id": "barcolana-58-lelio",
    "titolo": "Parte la Barcolana 58 con la mascotte “Lelio”",
    "categoria": "eventi",
    "data": "2026-05-15",
    "periodo": "Maggio 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», maggio 2026",
    "immagine": "assets/img/notizie/lelio-barcolana.jpg",
    "sintesi": "Presentata la nuova edizione e la mascotte dedicata a Lelio Luttazzi e al suo “El Can de Trieste”.",
    "testo": [
      "Parte la Barcolana 58 con la presentazione della prossima edizione e della mascotte “Lelio”, omaggio all’indimenticato Lelio Luttazzi e al suo “El Can de Trieste”."
    ],
    "galleria": []
  },
  {
    "id": "calicanto-sport-inclusione",
    "titolo": "Calicanto: da 25 anni lo sport che abbatte le barriere",
    "categoria": "eventi",
    "data": "2026-05-15",
    "periodo": "Maggio 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», maggio 2026",
    "immagine": "assets/img/notizie/calicanto.jpg",
    "sintesi": "Il torneo di pallavolo e pallacanestro integrato, una palestra di vita dove le differenze si annullano.",
    "testo": [
      "Assistere alle sfide del torneo di pallavolo e pallacanestro integrato di Calicanto è sempre un’emozione autentica: lo sport è lo strumento di inclusione sociale più potente.",
      "Grazie a chi da 25 anni abbatte ogni barriera: questo progetto non è solo agonismo, ma una palestra di vita dove le differenze si annullano nel gioco di squadra."
    ],
    "galleria": []
  },
  {
    "id": "burlo-io-corro",
    "titolo": "Io Corro per il Burlo Garofolo",
    "categoria": "territorio",
    "data": "2026-05-15",
    "periodo": "Maggio 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», maggio 2026",
    "immagine": "assets/img/notizie/burlo.jpg",
    "sintesi": "La solidarietà diventa più forte quando nasce dall’incontro tra comunità, imprese e istituzioni.",
    "testo": [
      "Sostenere il Burlo Garofolo significa dire grazie a chi, con professionalità e profonda umanità, si prende cura ogni giorno dei bambini e delle loro famiglie.",
      "Iniziative come “Io Corro” confermano il valore di un impegno condiviso: un gesto concreto di responsabilità sociale per rendere i reparti ancora più accoglienti."
    ],
    "galleria": []
  },
  {
    "id": "terremoto-friuli-50-anni",
    "titolo": "Cinquant’anni dal terremoto del Friuli: una lezione che guida ancora",
    "categoria": "territorio",
    "data": "2026-05-15",
    "periodo": "Maggio 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», maggio 2026",
    "immagine": "assets/img/notizie/terremoto-1976.jpg",
    "sintesi": "“Avevo diciott’anni quando, da volontario, arrivai tra le macerie.” Il ricordo dell’Assessore nel 50° anniversario.",
    "testo": [
      "Il 50° anniversario del terremoto del Friuli del 1976 non è solo una ricorrenza, ma un ritorno alle radici della formazione umana e civile dell’Assessore: “Avevo diciott’anni quando, da volontario, arrivai tra le macerie: ricordo ancora il silenzio irreale, la polvere e quella fatica condivisa che univa le persone.”",
      "In quei giorni nacque una forza collettiva straordinaria, capace di trasformare il dolore in un modello di ricostruzione unico al mondo. Lo stesso senso di responsabilità permette oggi di affrontare le sfide dell’ambiente e dello sviluppo, con la certezza di essere una terra che sa rinascere più forte di prima."
    ],
    "galleria": []
  },
  {
    "id": "capitaneria-cittadinanza-onoraria",
    "titolo": "Cittadinanza onoraria alla Capitaneria di Porto di Trieste",
    "categoria": "territorio",
    "data": "2026-05-15",
    "periodo": "Maggio 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», maggio 2026",
    "immagine": "assets/img/notizie/capitaneria.jpg",
    "sintesi": "Un riconoscimento a chi mette ogni giorno la propria vita al servizio della sicurezza in mare e della tutela del Golfo.",
    "testo": [
      "La cittadinanza onoraria conferita dal Comune di Trieste alla Capitaneria di Porto è un riconoscimento prestigioso a un’istituzione fondamentale per la città e alle donne e agli uomini che mettono ogni giorno la loro vita al servizio della comunità.",
      "Il loro lavoro è il pilastro della sicurezza di chi vive il mare ed è cruciale per la tutela del Golfo di Trieste e del suo ecosistema."
    ],
    "galleria": []
  },
  {
    "id": "momentum-memoria-foibe",
    "titolo": "“Momentum”: la memoria delle foibe e dell’esodo diventa permanente",
    "categoria": "territorio",
    "data": "2026-05-15",
    "periodo": "Maggio 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», maggio 2026",
    "immagine": "assets/img/notizie/momentum.jpg",
    "sintesi": "L’installazione a Trieste e la futura collocazione del Treno del Ricordo a Campo Marzio.",
    "testo": [
      "L’installazione “Momentum” a Trieste è un passo fondamentale per rendere permanente la memoria delle foibe e dell’esodo. Insegnare questa tragica pagina nelle scuole è un dovere morale, perché simili eccidi non si ripetano.",
      "L’auspicio è che l’opera e la futura collocazione del Treno del Ricordo a Campo Marzio contribuiscano a dare la verità che la nostra storia merita."
    ],
    "galleria": []
  },
  {
    "id": "parco-rimembranza-centenario",
    "titolo": "Cento anni del Parco della Rimembranza",
    "categoria": "territorio",
    "data": "2026-05-15",
    "periodo": "Maggio 2026",
    "verificato": true,
    "fonteNome": "Notiziario «La passione di sempre», maggio 2026",
    "immagine": "assets/img/notizie/rimembranza.jpg",
    "sintesi": "La memoria come fondamento dell’identità: la proposta di un luogo dedicato agli “eroi dimenticati”.",
    "testo": [
      "La memoria non è solo un atto dovuto, ma il fondamento dell’identità e del futuro di una comunità. Celebrare il centenario del Parco della Rimembranza insegna che una comunità consapevole è più forte e unita.",
      "La memoria non deve dividere, ma insegnare il valore della pace, della responsabilità e della dignità dei popoli. Come Regione, insieme alle associazioni, l’Assessore propone un luogo dedicato agli “eroi dimenticati”, che la grande storia ha lasciato ai margini ma che fanno parte a pieno titolo della nostra identità."
    ],
    "galleria": []
  }
];

/* Le notizie del notiziario precedono quelle dell’archivio */
window.NOTIZIE = window.NOTIZIE_NOTIZIARIO.concat(window.NOTIZIE);

window.CATEGORIE = {
  ambiente: { nome: "Ambiente", classe: "" },
  energia: { nome: "Energia", classe: "etichetta--energia" },
  territorio: { nome: "Territorio e Trieste", classe: "etichetta--territorio" },
  eventi: { nome: "Eventi e Sport", classe: "etichetta--eventi" }
};
