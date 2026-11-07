export default function Loading() {
  return (
    <main className="mx-auto w-full max-w-6xl space-y-8 px-4 py-8" aria-busy="true">
      <div className="space-y-2">
        <div className="bg-muted h-8 w-56 animate-pulse rounded" />
        <div className="bg-muted h-4 w-96 max-w-full animate-pulse rounded" />
      </div>
      <div className="bg-muted h-24 animate-pulse rounded-xl" />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }).map((_, index) => (
          <div key={index} className="bg-muted h-28 animate-pulse rounded-xl" />
        ))}
      </div>
      <div className="grid gap-4 xl:grid-cols-2">
        <div className="bg-muted h-80 animate-pulse rounded-xl xl:col-span-2" />
        <div className="bg-muted h-72 animate-pulse rounded-xl" />
        <div className="bg-muted h-72 animate-pulse rounded-xl" />
      </div>
    </main>
  );
}
