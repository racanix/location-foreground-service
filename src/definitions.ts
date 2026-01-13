export interface LocationForegroundServicePlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
  print(options: { value: string }): Promise<{ value: string }>;
}
