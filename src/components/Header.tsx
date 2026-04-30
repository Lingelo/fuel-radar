// Wordmark-only logo (R4 baseline). No pictogram — keeps the identity
// neutral and avoids the gouv.fr signal Marianne+#000091 would create.
export function Header() {
  return (
    <a
      href={`${import.meta.env.BASE_URL}`}
      aria-label="Carburants France — retour à l'accueil"
      className="flex items-baseline gap-1.5 text-lg font-bold tracking-tight text-gray-900 hover:opacity-80"
    >
      <span aria-hidden="true" className="h-2 w-2 self-center rounded-full bg-primary" />
      Carburants
      <span className="text-sm font-normal tracking-normal text-gray-500">France</span>
    </a>
  );
}
