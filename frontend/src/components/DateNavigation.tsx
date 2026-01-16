import './DateNavigation.css';

interface DateNavigationProps {
  currentDate: Date;
  onDateChange: (date: Date) => void;
  onPreviousDay: () => void;
  onNextDay: () => void;
  onToday: () => void;
}

function DateNavigation({
  currentDate,
  onDateChange,
  onPreviousDay,
  onNextDay,
  onToday,
}: DateNavigationProps) {
  const formatDateForInput = (date: Date) => {
    return date.toISOString().split('T')[0];
  };

  const handleDateChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newDate = new Date(e.target.value);
    onDateChange(newDate);
  };

  return (
    <div className="date-navigation">
      <button onClick={onPreviousDay} className="nav-button">
        Previous Day
      </button>
      <input
        type="date"
        value={formatDateForInput(currentDate)}
        onChange={handleDateChange}
        className="date-picker"
      />
      <button onClick={onNextDay} className="nav-button">
        Next Day
      </button>
      <button onClick={onToday} className="nav-button today-button">
        Today
      </button>
    </div>
  );
}

export default DateNavigation;
